package org.abhijitsarkar.camel.translator

import com.jcraft.jsch.Session
import org.abhijitsarkar.camel.Application
import org.abhijitsarkar.camel.model.AuditRecord
import org.abhijitsarkar.camel.model.Event
import org.abhijitsarkar.camel.model.Project
import org.abhijitsarkar.camel.util.updateIfNecessary
import org.apache.camel.Body
import org.apache.camel.Header
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
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
    fun update(path: Path, events: List<Event>): List<AuditRecord>
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

    override fun clone(@Body project: Project): Path {
        return Files.createTempDirectory(null).toFile()
                .apply { deleteOnExit() }
                .let { File(it, project.name) }
                .let { projectDir ->
                    log.info("Cloning repo: {} to: {}.", project.sshUrl, projectDir.absolutePath)

                    Git.cloneRepository()
                            .setURI(project.sshUrl)
                            .setDirectory(projectDir)
                            .setCloneAllBranches(true)
                            .call().use {
                        projectDir.toPath()
                    }
                }
    }

    private val Path.isDockerImages: Boolean get() = this.toFile().name == Application.DOCKER_IMAGES_FILENAME

    private fun Path.toRepo(): Repository = FileRepositoryBuilder()
            .setWorkTree(this.toFile())
            .build()

    private val Git.remoteBranches: List<Ref>
        get() = this
                .branchList()
                .setListMode(ListBranchCommand.ListMode.REMOTE)
                .call()

    private val Git.localBranches: List<Ref>
        get() = this
                .branchList()
                .call()

    private val String.isMasterOrCi: Boolean get() = this.matches("^.+(?:master|ci)\$".toRegex())

    val dumperOptions = DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
    }

    override fun update(@Body path: Path, @Header(Application.DOCKER_REGISTRY_EVENTS_HEADER) events: List<Event>): List<AuditRecord> {
        val matcher = BiPredicate<Path, BasicFileAttributes> { p, attr ->
            attr.isRegularFile && p.isDockerImages
        }

        val auditRecords = mutableListOf<AuditRecord>()

        Files.find(path, 1, matcher)
                .forEach { p ->
                    val file = p.toFile()
                    path.toRepo()
                            .use { repo ->
                                Git(repo).use { git ->
                                    git.remoteBranches
                                            .map(Ref::getName)
                                            .filter { it.isMasterOrCi }
                                            .forEach { branchName ->
                                                val localBranchName = branchName.split("origin/")[1]
                                                val branchExists = git
                                                        .localBranches
                                                        .map(Ref::getName)
                                                        .any { it.endsWith(localBranchName) }

                                                git.checkout()
                                                        .setCreateBranch(!branchExists)
                                                        .setName(localBranchName)
                                                        .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                                                        .setStartPoint(branchName)
                                                        .call()

                                                val (imagesUpdated, dockerImages) = file.updateIfNecessary(events)

                                                if (imagesUpdated.isNotEmpty()) {
                                                    file.writer().use {
                                                        Yaml(dumperOptions).dump(dockerImages, it)
                                                    }

                                                    git.add()
                                                            .addFilepattern(Application.DOCKER_IMAGES_FILENAME)
                                                            .call()

                                                    val eventId = imagesUpdated.keys.first()

                                                    git.commit()
                                                            .setMessage("Reference to eventId - $eventId")
                                                            .call()

                                                    git.push()
                                                            .call()

                                                    auditRecords += AuditRecord(
                                                            eventId,
                                                            path.toFile().name,
                                                            branchName,
                                                            imagesUpdated[eventId]
                                                    )
                                                            .also {
                                                                log.info("{}.", it)
                                                            }
                                                }
                                            }
                                }
                            }
                }
        return auditRecords
    }
}