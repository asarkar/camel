package org.abhijitsarkar.translator

import com.jcraft.jsch.Session
import org.abhijitsarkar.model.AuditRecord
import org.abhijitsarkar.model.Event
import org.abhijitsarkar.model.Project
import org.apache.camel.Exchange
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.JschConfigSessionFactory
import org.eclipse.jgit.transport.OpenSshConfig
import org.eclipse.jgit.transport.SshSessionFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
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
            val refs = Git.lsRemoteRepository()
                    .setHeads(true)
                    .setTags(false)
                    .setRemote(project.sshUrl)
                    .call()
                    .map { it.name }
                    .also { log.debug("Repo: {} has the following branches: {}.", project.sshUrl, it) }

            val projectDir = Files.createTempDirectory(null).toFile()
                    .apply { deleteOnExit() }
                    .let { File(it, project.name) }

            log.info("Cloning repo: {} to: {}.", project.sshUrl, projectDir.absolutePath)

            git = refs
                    .filter { it.isMasterOrCi }
                    .let {
                        Git.cloneRepository()
                                .setURI(project.sshUrl)
                                .setDirectory(projectDir)
                                .setCloneAllBranches(false)
                                .setBranchesToClone(it)
                                .call()
                    }

            return projectDir.toPath()
        } finally {
            git?.close()
        }
    }

    // TODO: match actual name from constant
    private val Path.isDockerImages: Boolean get() = this.toFile().name == "docker-images.yml"

    private fun updateIfNecessary(file: File, events: List<Event>) =
            file.bufferedReader().use {
                val dockerImages = Yaml().load(it) as MutableMap<String, String>
                var keysUpdated = mutableMapOf<String, List<String>>()

                dockerImages.keys.forEach { k ->
                    val v = dockerImages[k]
                    val groups = "^(.+)(?<=/)(.+):(.+)\$".toRegex()
                            .matchEntire(v as CharSequence)
                            ?.groupValues

                    if (groups != null && groups.size >= 4) {
                        val repository = groups[2]
                        val tag = groups[3]

                        val event = events
                                .find { e -> e.target.repository == repository && e.target.tag != tag }

                        if (event != null) {
                            dockerImages[k] = "${groups[1]}${groups[2]}:$tag"
                            keysUpdated.merge(event.id, listOf(k), { k1, k2 -> k1 + k2 })
                        }
                    }
                }

                // no first-class support for mutable to immutable map
                keysUpdated.to(dockerImages)
            }

    override fun update(exchange: Exchange) {
        val incoming = exchange.`in`
        val path = incoming.getBody(Path::class.java)

        // generic type arguments are not reified at runtime,
        // we can only obtain an object representing a class, not a type
        val events = incoming.getHeader("DockerRegistryEvents", List::class.java) as List<Event>

        val matcher = BiPredicate<Path, BasicFileAttributes> { p, attr ->
            attr.isRegularFile && p.isDockerImages
        }

        Files.find(path, 1, matcher)
                .forEach { p ->
                    val file = p.toFile()
                    val repo = FileRepositoryBuilder()
                            .setGitDir(path.toFile())
                            .readEnvironment()
                            .findGitDir()
                            .build()
                    val git = Git(repo)

                    try {
                        repo.allRefs
                                .filter { e -> e.key.isMasterOrCi }
                                .forEach { name, _ ->
                                    git
                                            .checkout()
                                            .setCreateBranch(true)
                                            .setName(name)
                                            .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                                            .setStartPoint("origin/$name")
                                            .call()

                                    val (keysUpdated, dockerImages) = updateIfNecessary(file, events)

                                    if (keysUpdated.isNotEmpty()) {
                                        file.writer().use {
                                            Yaml().dump(dockerImages, it)
                                        }

                                        git.add()
                                                .addFilepattern(".")
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
                                                name,
                                                keysUpdated[eventId]
                                        )

                                        log.info("Audit record: {}.", auditRecord)

                                        incoming.body = auditRecord
                                    }
                                }
                    } finally {
                        repo?.close()
                        git?.close()
                    }
                }

    }
}