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
package io.github.malonetalk.convertor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.malonetalk.dto.McpServerRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class McpServerConverterTest {
    @Test
    void preservesArgumentsAndMasksSecretsWithoutLosingThemOnEdit() {
        var converter = Mappers.getMapper(McpServerConverter.class);
        var entity =
                converter.toEntity(
                        McpServerRequest.builder()
                                .args(List.of("hello world", "a,b"))
                                .env(Map.of("TOKEN", "private-token"))
                                .headers(Map.of("Authorization", "Bearer abc"))
                                .queryParams(Map.of("key", "private"))
                                .enableTools(List.of("search"))
                                .build());
        var response = converter.toResponse(entity);
        assertEquals(List.of("hello world", "a,b"), response.args());
        assertEquals("********", response.env().get("TOKEN"));
        assertFalse(response.toString().contains("private-token"));
        assertEquals(
                entity.getEnv(),
                McpJson.preserveSecrets(McpJson.encode(response.env()), entity.getEnv()));
        assertEquals("{}", McpJson.preserveSecrets("{}", entity.getEnv()));
        assertEquals(entity.getEnv(), McpJson.preserveSecrets(null, entity.getEnv()));
    }
}
