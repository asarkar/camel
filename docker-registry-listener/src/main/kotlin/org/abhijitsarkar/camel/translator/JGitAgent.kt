package org.abhijitsarkar.camel.translator

import com.jcraft.jsch.Session
import org.abhijitsarkar.camel.Application
import org.abhijitsarkar.camel.model.AuditRecord
import org.abhijitsarkar.camel.model.Event
import org.abhijitsarkar.camel.model.Project
import org.abhijitsarkar.camel.util.updateIfNecessary
import org.apache.camel.Exchange
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.JschConfigSessionFactory
import org.eclipse.jgit.transport.OpenSshConfig
import org.eclipse.jgit.transport.SshSessionFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.function.BiPredicate


/**
 * @author Abhijit Sarkar
 */
interface JGitAgent {
    fun clone(project: Project): Path
    fun update(exchange: Exchange)
}

@Service
class JGitAgentImpl : JGitAgent {
    init {
        SshSessionFactory.setInstance(object : JschConfigSessionFactory() {
            override fun configure(hc: OpenSshConfig.Host, session: Session) {
                session.setConfig("StrictHostKeyChecking", "no")
            }
        })
    }

    private val log = LoggerFactory.getLogger(JGitAgent::class.java)

    private val String.isMasterOrCi: Boolean get() = this.matches("^.+(?:master|ci)\$".toRegex())

    override fun clone(project: Project): Path {
        var git: Git? = null

        try {
            val projectDir = Files.createTempDirectory(null).toFile()
                    .apply { deleteOnExit() }
                    .let { File(it, project.name) }

            log.info("Cloning repo: {} to: {}.", project.sshUrl, projectDir.absolutePath)

            git = Git.cloneRepository()
                    .setURI(project.sshUrl)
                    .setDirectory(projectDir)
                    .setCloneAllBranches(true)
                    .call()

            return projectDir.toPath()
        } finally {
            git?.close()
        }
    }

    private val Path.isDockerImages: Boolean get() = this.toFile().name == Application.DOCKER_IMAGES_FILENAME

    val dumperOptions = DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
    }

    override fun update(exchange: Exchange) {
        val incoming = exchange.`in`
        val path = incoming.getBody(Path::class.java)

        // generic type arguments are not reified at runtime,
        // we can only obtain an object representing a class, not a type
        val events = incoming.getHeader(Application.DOCKER_REGISTRY_EVENTS_HEADER, List::class.java) as List<Event>

        val matcher = BiPredicate<Path, BasicFileAttributes> { p, attr ->
            attr.isRegularFile && p.isDockerImages
        }

        Files.find(path, 1, matcher)
                .forEach { p ->
                    val file = p.toFile()
                    val repo = FileRepositoryBuilder()
                            .setWorkTree(path.toFile())
                            .build()
                    val git = Git(repo)

                    try {
                        git
                                .branchList()
                                .setListMode(ListBranchCommand.ListMode.REMOTE)
                                .call()
                                .map { it.name }
                                .filter { branchName -> branchName.isMasterOrCi }
                                .forEach { branchName ->
                                    val localBranchName = branchName.takeLastWhile { it != '/' }
                                    val branchExists = git
                                            .branchList()
                                            .call()
                                            .map { it.name }
                                            .any { it.endsWith(localBranchName) }

                                    git.checkout()
                                            .setCreateBranch(!branchExists)
                                            .setName(localBranchName)
                                            .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                                            .setStartPoint(branchName)
                                            .call()

                                    val (keysUpdated, dockerImages) = file.updateIfNecessary(events)

                                    if (keysUpdated.isNotEmpty()) {
                                        file.writer().use {
                                            Yaml(dumperOptions).dump(dockerImages, it)
                                        }

                                        git.add()
                                                .addFilepattern(Application.DOCKER_IMAGES_FILENAME)
                                                .call()

                                        val eventId = keysUpdated.keys.first()

                                        git.commit()
                                                .setMessage("Updated in response to event - $eventId")
                                                .call()

                                        git.push()
                                                .call()

                                        val auditRecord = AuditRecord(
                                                eventId,
                                                path.toFile().name,
                                                branchName,
                                                keysUpdated[eventId]
                                        )

                                        log.info("{}.", auditRecord)

                                        incoming.body = auditRecord
                                    } else {
                                        incoming.body = ""
                                    }
                                }
                    } finally {
                        repo?.close()
                        git?.close()
                    }
                }

    }
}