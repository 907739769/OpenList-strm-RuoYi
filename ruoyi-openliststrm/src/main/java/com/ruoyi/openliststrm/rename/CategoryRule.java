package com.ruoyi.openliststrm.rename;

import com.ruoyi.openliststrm.rename.model.MediaInfo;
import lombok.Getter;

import java.util.*;

/**
 * 简单规则匹配器，基于 MediaInfo 提供的字段（tmdb 扩展字段可能包括 genreIds, originalLanguage, originCountries）
 */
@Getter
public class CategoryRule {
    private final String name;
    private final Set<String> genreIds = new HashSet<>();
    private final Set<String> originalLanguages = new HashSet<>();
    private final Set<String> originCountries = new HashSet<>();

    public CategoryRule(String name) {
        this.name = name;
    }

    public CategoryRule withGenreIds(String... ids) {
        if (ids != null) {
            for (String id : ids) {
                if (id != null && !id.isEmpty()) genreIds.add(id.trim());
            }
        }
        return this;
    }

    public CategoryRule withOriginalLanguage(String... langs) {
        if (langs != null) {
            for (String l : langs) {
                if (l != null && !l.isEmpty()) originalLanguages.add(l.trim().toLowerCase());
            }
        }
        return this;
    }

    public CategoryRule withOriginCountry(String... countries) {
        if (countries != null) {
            for (String c : countries) {
                if (c != null && !c.isEmpty()) originCountries.add(c.trim().toUpperCase());
            }
        }
        return this;
    }

    public boolean matches(MediaInfo info) {
        // 1. 检查 genreIds
        if (!genreIds.isEmpty()) {
            List<String> infoGenres = getGenreIdsFromInfo(info);
            if (infoGenres.isEmpty() || Collections.disjoint(infoGenres, genreIds)) {
                return false;
            }
        }

        // 2. 检查 originalLanguages
        if (!originalLanguages.isEmpty()) {
            String lang = getOriginalLanguageFromInfo(info);
            if (lang == null || !originalLanguages.contains(lang.toLowerCase())) {
                return false;
            }
        }

        // 3. 检查 originCountries
        if (!originCountries.isEmpty()) {
            List<String> countries = getOriginCountriesFromInfo(info);
            if (countries == null || countries.isEmpty()) return false;

            boolean matchedCountry = countries.stream()
                    .anyMatch(c -> originCountries.contains(c.toUpperCase()));
            if (!matchedCountry) return false;
        }

        // 4. 如果没有任何条件，或者全部条件都满足
        return true;
    }

    // helper extractors -- these attempt to read extended TMDb fields stored in MediaInfo via reflection or map
    private List<String> getGenreIdsFromInfo(MediaInfo info) {
        try {
            // try a getter first
            java.lang.reflect.Method m = info.getClass().getMethod("getGenreIds");
            Object res = m.invoke(info);
            if (res instanceof List) {
                List<?> l = (List<?>) res;
                List<String> out = new ArrayList<>();
                for (Object o : l) out.add(String.valueOf(o));
                return out;
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            // ignore reflection errors
        }
        // try map style: maybe MediaInfo has a metadata map
        try {
            java.lang.reflect.Method m2 = info.getClass().getMethod("getMetadata");
            Object meta = m2.invoke(info);
            if (meta instanceof Map) {
                Object g = ((Map<?, ?>) meta).get("genre_ids");
                if (g instanceof List) {
                    List<String> out = new ArrayList<>();
                    for (Object o : (List<?>) g) out.add(String.valueOf(o));
                    return out;
                }
            }
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }

    private String getOriginalLanguageFromInfo(MediaInfo info) {
        try {
            java.lang.reflect.Method m = info.getClass().getMethod("getOriginalLanguage");
            Object res = m.invoke(info);
            if (res != null) return String.valueOf(res);
        } catch (Exception ignored) {}
        // fallback to metadata
        try {
            java.lang.reflect.Method m2 = info.getClass().getMethod("getMetadata");
            Object meta = m2.invoke(info);
            if (meta instanceof Map) {
                Object ol = ((Map<?, ?>) meta).get("original_language");
                if (ol != null) return String.valueOf(ol);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private List<String> getOriginCountriesFromInfo(MediaInfo info) {
        try {
            java.lang.reflect.Method m = info.getClass().getMethod("getOriginCountries");
            Object res = m.invoke(info);
            if (res instanceof List) {
                List<?> l = (List<?>) res;
                List<String> out = new ArrayList<>();
                for (Object o : l) out.add(String.valueOf(o));
                return out;
            }
        } catch (Exception ignored) {}
        try {
            java.lang.reflect.Method m2 = info.getClass().getMethod("getMetadata");
            Object meta = m2.invoke(info);
            if (meta instanceof Map) {
                Object oc = ((Map<?, ?>) meta).get("origin_country");
                if (oc instanceof List) {
                    List<String> out = new ArrayList<>();
                    for (Object o : (List<?>) oc) out.add(String.valueOf(o));
                    return out;
                }
                if (oc instanceof String) {
                    return Arrays.asList(((String) oc).split(","));
                }
            }
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }
}
