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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class SkillRepositoryConfigurationTest {
    @TempDir Path workspace;

    @Test
    void repositoriesAreCreatedOnceAndClosedAfterTheirConsumer() {
        var properties = new SkillProperties();
        var source = new SkillProperties.FileSystemSource();
        source.setPath(workspace.toString());
        properties.setFilesystem(List.of(source, source));
        try (var construction = mockConstruction(FileSystemSkillRepository.class);
                var context = context(properties)) {
            var consumer = context.getBean(RepositoryConsumer.class);
            var repositories = consumer.repositories;
            assertSame(repositories, context.getBean("skillRepositories"));
            assertSame(consumer, context.getBean(RepositoryConsumer.class));
            assertEquals(2, construction.constructed().size());
            assertEquals(construction.constructed(), repositories);
            repositories.forEach(repository -> verify(repository, never()).close());
            // One failed close must not prevent another repository from being released.
            doThrow(new IllegalStateException("close failed"))
                    .when(repositories.getFirst())
                    .close();
            context.close();
            assertTrue(consumer.closedBeforeRepositories);
            context.close();
            repositories.forEach(repository -> verify(repository).close());
        }
    }

    @Test
    void emptySourcesStillProvideAnInjectableList() {
        try (var context = context(new SkillProperties())) {
            assertTrue(context.getBean(RepositoryConsumer.class).repositories.isEmpty());
        }
    }

    private AnnotationConfigApplicationContext context(SkillProperties properties) {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(SkillProperties.class, () -> properties);
        context.register(SkillRepositoryConfiguration.class, ConsumerConfiguration.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerConfiguration {
        @Bean
        RepositoryConsumer consumer(
                @Qualifier("skillRepositories") List<AgentSkillRepository> repositories) {
            return new RepositoryConsumer(repositories);
        }
    }

    @RequiredArgsConstructor
    static class RepositoryConsumer implements AutoCloseable {
        private final List<AgentSkillRepository> repositories;
        private boolean closedBeforeRepositories;

        @Override
        public void close() {
            repositories.forEach(repository -> verify(repository, never()).close());
            closedBeforeRepositories = true;
        }
    }
}
