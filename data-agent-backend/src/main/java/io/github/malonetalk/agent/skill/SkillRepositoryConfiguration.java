/*
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * limitations under the License.
 */
package io.github.malonetalk.agent.skill;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.exception.NacosException;
import io.agentscope.core.nacos.skill.NacosSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import io.agentscope.core.skill.repository.GitSkillRepository;
import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Adapts application skill sources to AgentScope repositories; Harness manages skill loading. */
@Slf4j
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class SkillRepositoryConfiguration {

    private final SkillProperties skillProperties;
    private final List<AgentSkillRepository> repositories = new ArrayList<>();

    @Bean
    public List<AgentSkillRepository> skillRepositories() {
        configureFilesystem();
        configureGit();
        configureClasspath();
        configureNacos();
        return List.copyOf(repositories);
    }

    private void configureFilesystem() {
        for (SkillProperties.FileSystemSource fs : skillProperties.getFilesystem()) {
            try {
                Path resolvedPath = Path.of(fs.getPath()).toAbsolutePath().normalize();
                log.info(
                        "FileSystemSkillRepository path: {} (resolved to: {})",
                        fs.getPath(),
                        resolvedPath);
                FileSystemSkillRepository repo =
                        new FileSystemSkillRepository(
                                resolvedPath, fs.isWriteable(), fs.getSource());
                repositories.add(repo);
            } catch (Exception e) {
                log.error("Failed to create FileSystemSkillRepository: {}", fs.getPath(), e);
            }
        }
    }

    private void configureGit() {
        for (SkillProperties.GitSource gs : skillProperties.getGit()) {
            try {
                Path localPath = gs.getLocalPath() != null ? Path.of(gs.getLocalPath()) : null;
                GitSkillRepository repo =
                        new GitSkillRepository(
                                gs.getUrl(),
                                gs.getBranch(),
                                localPath,
                                gs.getSource(),
                                gs.isAutoSync());
                repositories.add(repo);
                log.info("Created GitSkillRepository: {}", gs.getUrl());
            } catch (Exception e) {
                log.error("Failed to create GitSkillRepository: {}", gs.getUrl(), e);
            }
        }
    }

    private void configureClasspath() {
        for (SkillProperties.ClasspathSource cs : skillProperties.getClasspath()) {
            try {
                ClasspathSkillRepository repo =
                        new ClasspathSkillRepository(cs.getResourcePath(), cs.getSource());
                repositories.add(repo);
                log.info("Created ClasspathSkillRepository: {}", cs.getResourcePath());
            } catch (Exception e) {
                log.error("Failed to create ClasspathSkillRepository: {}", cs.getResourcePath(), e);
            }
        }
    }

    private void configureNacos() {
        for (SkillProperties.NacosSource ns : skillProperties.getNacos()) {
            try {
                AiService aiService = createNacosAiService(ns);
                Properties props = new Properties();
                if (ns.getSkillVersion() != null) {
                    props.setProperty(
                            NacosSkillRepository.SKILL_VERSION_PATH, ns.getSkillVersion());
                }
                if (ns.getSkillLabel() != null) {
                    props.setProperty(NacosSkillRepository.SKILL_LABEL_PATH, ns.getSkillLabel());
                }
                repositories.add(
                        new NacosSkillRepository(
                                aiService, ns.getNamespace(), props, ns.getSkillNames()));
            } catch (Exception e) {
                log.error(
                        "Failed to create NacosSkillRepository for namespace '{}'",
                        ns.getNamespace(),
                        e);
            }
        }
    }

    private AiService createNacosAiService(SkillProperties.NacosSource ns) throws NacosException {
        Properties properties = new Properties();

        properties.setProperty(PropertyKeyConst.SERVER_ADDR, ns.getServerAddr());
        if (ns.getUsername() != null) {
            properties.setProperty(PropertyKeyConst.USERNAME, ns.getUsername());
        }
        if (ns.getPassword() != null) {
            properties.setProperty(PropertyKeyConst.PASSWORD, ns.getPassword());
        }
        if (ns.getNamespace() != null) {
            properties.setProperty(PropertyKeyConst.NAMESPACE, ns.getNamespace());
        }
        return AiFactory.createAiService(properties);
    }

    @PreDestroy
    public void destroy() {
        for (AgentSkillRepository repo : repositories) {
            try {
                repo.close();
            } catch (Exception e) {
                log.warn("Failed to close repository: {}", repo.getRepositoryInfo(), e);
            }
        }
        repositories.clear();
    }
}
