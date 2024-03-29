package nl.knaw.huc.sdswitch.dreamfactory;

import mjson.Json;
import net.sf.saxon.s9api.SaxonApiException;
import nl.knaw.huc.sdswitch.recipe.ConfigMappingRecipe;
import nl.knaw.huc.sdswitch.recipe.RecipeData;
import nl.knaw.huc.sdswitch.recipe.RecipeException;
import nl.knaw.huc.sdswitch.recipe.RecipeMappingException;
import nl.knaw.huc.sdswitch.recipe.RecipeResponse;
import nl.knaw.huc.sdswitch.recipe.RecipeValidationException;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DreamFactoryRecipe implements
        ConfigMappingRecipe<DreamFactoryRecipe.DreamFactoryConfig, DreamFactoryRecipe.DreamFactoryMappedConfig> {
    record DreamFactoryConfig(String type, String table, String baseUrl, String accept, String apiKey,
                              List<String> related, String format, JsonToHtml jsonToHtml, JsonToTtl jsonToTtl) {
    }

    record DreamFactoryMappedConfig(String type, String table, String baseUrl, String accept, String apiKey,
                                    List<String> related, String format, String xml2HtmlPath, String ttlSchemaPath) {
    }

    @Override
    public DreamFactoryConfig getConfig(DreamFactoryMappedConfig mapped) throws RecipeMappingException {
        try {
            if (mapped.xml2HtmlPath == null)
                throw new RecipeMappingException("Missing required 'xml2HtmlPath'");

            if (mapped.ttlSchemaPath == null)
                throw new RecipeMappingException("Missing required 'ttlSchemaPath'");

            JsonToHtml jsonToHtml = new JsonToHtml(mapped.xml2HtmlPath);
            JsonToTtl jsonToTtl = new JsonToTtl(mapped.ttlSchemaPath);

            return new DreamFactoryConfig(mapped.type, mapped.table, mapped.baseUrl, mapped.accept,
                    mapped.apiKey, mapped.related, mapped.format, jsonToHtml, jsonToTtl);
        } catch (ParserConfigurationException | IOException | SaxonApiException | SAXException ex) {
            throw new RecipeMappingException(ex.getMessage(), ex);
        }
    }

    @Override
    public void validateConfig(DreamFactoryRecipe.DreamFactoryConfig config, Set<String> pathParams)
            throws RecipeValidationException {
        try {
            if (!pathParams.contains("id"))
                throw new RecipeValidationException("Missing required path parameter 'id'");

            if (config.type == null)
                throw new RecipeValidationException("Missing required 'type'");

            if (config.table == null && !pathParams.contains("table"))
                throw new RecipeValidationException("Missing required 'table' config or path parameter");

            if (config.baseUrl == null)
                throw new RecipeValidationException("Missing required 'baseUrl'");

            if (config.apiKey == null)
                throw new RecipeValidationException("Missing required 'apiKey'");

            if (config.format == null || !List.of("json", "html", "ttl").contains(config.format))
                throw new RecipeValidationException("Missing required 'format' (json / html / ttl)");

            if (config.jsonToHtml == null)
                throw new RecipeValidationException("Invalid 'xml2HtmlPath'");

            if (config.jsonToTtl == null)
                throw new RecipeValidationException("Invalid 'ttlSchemaPath'");
        } catch (Exception ex) {
            throw new RecipeValidationException(ex.getMessage(), ex);
        }
    }

    @Override
    public RecipeResponse withData(RecipeData<DreamFactoryConfig> data) throws RecipeException {
        try {
            String table = data.config().table() == null ? data.pathParam("table") : data.config().table();
            String url = String.format("%s/api/v2/%s/_table/%s",
                    data.config().baseUrl(), data.config().type(), URLEncoder.encode(table, StandardCharsets.UTF_8));

            if (data.pathParam("id") != null) {
                String related = "";
                if (!data.config().related().isEmpty()) {
                    related = "&related=" + URLEncoder.encode(
                            String.join(",", data.config().related()), StandardCharsets.UTF_8);
                }
                // related=* gives 'not implemented'
                url += String.format("/%s?fields=*%s",
                        URLEncoder.encode(data.pathParam("id"), StandardCharsets.UTF_8),
                        related);
            }

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", data.config().accept() != null && !data.config().accept().isEmpty()
                    ? data.config().accept() : "application/json");
            conn.setRequestProperty("X-DreamFactory-API-Key", data.config().apiKey());

            if (conn.getResponseCode() != 200) {
                throw new RecipeException(conn.getResponseMessage(), conn.getResponseCode());
            }

            String text = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            Json jsonObject = Json.read(text);
            // Get the references from config
            for (String relation : data.config().related()) {
                fillReference(jsonObject, relation);
            }

            String body = switch (data.config().format()) {
                case "json" -> jsonObject.toString();
                case "ttl" -> data.config().jsonToTtl().toTtl(jsonObject.toString());
                case "html" -> data.config().jsonToHtml().toHtml(jsonObject.toString());
                default -> throw new RecipeException("Impossible format!");
            };

            String contentType = switch (data.config().format()) {
                case "json" -> "text/json";
                case "ttl" -> "text/ttl";
                case "html" -> "text/html";
                default -> throw new RecipeException("Impossible format!");
            };

            return RecipeResponse.withBody(body, contentType);
        } catch (IOException | SaxonApiException ex) {
            throw new RecipeException(ex.getMessage(), ex);
        }
    }

    static void fillReference(Json jsonObject, String reference) {
        String[] toFrom = new String[2];
        try {
            Json referedObject = jsonObject.atDel(reference);
            toFrom = reference.split("_by_");
            jsonObject.atDel(toFrom[1]);
            jsonObject.set(toFrom[0], referedObject.at("naam").getValue());
        } catch (UnsupportedOperationException | NullPointerException ex) {
            // other empty fields contain null, so:
            jsonObject.set(toFrom[0], null);
        }
    }
}
