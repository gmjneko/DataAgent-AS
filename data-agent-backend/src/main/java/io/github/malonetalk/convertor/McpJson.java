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

import com.fasterxml.jackson.core.type.TypeReference;
import io.agentscope.core.util.JsonUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class McpJson {
    public static final String MASK = "********";

    private McpJson() {}

    public static String encode(Object value) {
        return value == null ? null : JsonUtils.getJsonCodec().toJson(value);
    }

    public static List<String> list(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        // Legacy rows used comma-separated arguments; new writes always use JSON.
        if (!value.stripLeading().startsWith("[")) {
            return List.of(value.split(","));
        }
        return JsonUtils.getJsonCodec().fromJson(value, new TypeReference<List<String>>() {});
    }

    public static Map<String, String> map(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        return JsonUtils.getJsonCodec()
                .fromJson(value, new TypeReference<Map<String, String>>() {});
    }

    public static Map<String, String> masked(String value) {
        Map<String, String> result = new LinkedHashMap<>();
        map(value).keySet().forEach(key -> result.put(key, MASK));
        return result;
    }

    public static String preserveSecrets(String incoming, String stored) {
        if (incoming == null) {
            return stored;
        }
        Map<String, String> previous = map(stored);
        Map<String, String> result = new LinkedHashMap<>(map(incoming));
        result.replaceAll(
                (key, value) -> MASK.equals(value) ? previous.getOrDefault(key, "") : value);
        return encode(result);
    }
}
