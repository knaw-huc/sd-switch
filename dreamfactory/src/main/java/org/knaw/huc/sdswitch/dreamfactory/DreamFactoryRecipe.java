package org.knaw.huc.sdswitch.dreamfactory;

import mjson.Json;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XsltTransformer;
import nl.mpi.tla.util.Saxon;
import org.knaw.huc.sdswitch.recipe.*;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

public class DreamFactoryRecipe implements Recipe<DreamFactoryRecipe.DreamFactoryConfig> {
    record DreamFactoryConfig(String type, String table, String baseUrl, String accept, String apiKey, String related,
                              Format format, JsonToHtml toHtml, JsonToTtl toTtl) {
        private enum Format {JSON, HTML, TTL}
    }

    @Override
    public DreamFactoryConfig parseConfig(XdmItem config, Set<String> pathParams) throws RecipeParseException {
        try {
            if (!pathParams.contains("id")) {
                throw new RecipeParseException("Missing required path parameter 'id'");
            }

            String type = Saxon.xpath2string(config, "type").trim();
            if (type.isBlank()) {
                throw new RecipeParseException("Missing required type");
            }

            String table = Saxon.xpath2string(config, "table").trim();
            if (table.isBlank() && !pathParams.contains("table")) {
                throw new RecipeParseException("Missing required table config or path parameter");
            }

            String baseUrl = Saxon.xpath2string(config, "base-url").trim();
            if (baseUrl.isBlank()) {
                throw new RecipeParseException("Missing required base-url");
            }

            String apiKey = Saxon.xpath2string(config, "api-key").trim();
            if (apiKey.isBlank()) {
                throw new RecipeParseException("Missing required api-key");
            }

            String related = Saxon.xpath2string(config, "related").trim();
            String accept = Saxon.xpath2string(config, "accept").trim();

            DreamFactoryConfig.Format format = switch (Saxon.xpath2string(config, "format").trim()) {
                case "json" -> DreamFactoryConfig.Format.JSON;
                case "html" -> DreamFactoryConfig.Format.HTML;
                case "ttl" -> DreamFactoryConfig.Format.TTL;
                default -> throw new RecipeParseException("Missing required format (json / html / ttl)");
            };

            String xml2HtmlPath = Saxon.xpath2string(config, "xml2html-path").trim();
            XsltTransformer toHtmlTransformer = Saxon.buildTransformer(new File(xml2HtmlPath)).load();
            JsonToHtml toHtml = new JsonToHtml(toHtmlTransformer);

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder db = dbf.newDocumentBuilder();

            String ttlSchemaPath = Saxon.xpath2string(config, "ttl-schema-path").trim();
            Document ttlSchema = db.parse(new FileInputStream(ttlSchemaPath));
            JsonToTtl toTtl = new JsonToTtl(ttlSchema);

            return new DreamFactoryConfig(type, table, baseUrl, accept, apiKey, related, format, toHtml, toTtl);
        } catch (Exception ex) {
            throw new RecipeParseException(ex.getMessage(), ex);
        }
    }

    @Override
    public RecipeResponse withData(RecipeData<DreamFactoryConfig> data) throws RecipeException {
        try {
            String table = data.config().table() == null ? data.pathParams().get("table") : data.config().table();
            String url = String.format("%s/api/v2/%s/_table/%s",
                    data.config().baseUrl(), data.config().type(), URLEncoder.encode(table, StandardCharsets.UTF_8));

            if (data.pathParams().get("id") != null) {
                String related = data.config().related();
                if (related == null) {
                    related = "";
                } else {
                    related = "&related=" + URLEncoder.encode(related, StandardCharsets.UTF_8);
                }
                // related=* gives 'not implemented'
                url += String.format("/%s?fields=*%s",
                        URLEncoder.encode(data.pathParams().get("id"), StandardCharsets.UTF_8),
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
            String related = data.config().related();
            if (related == null) {
                related = "";
            }
            String[] relations = related.split(",");
            for (String relation : relations) {
                fillReference(jsonObject, relation);
            }

            String body = switch (data.config().format()) {
                case JSON -> jsonObject.toString();
                case TTL -> data.config().toTtl().toTtl(jsonObject.toString());
                case HTML -> data.config().toHtml().toHtml(jsonObject.toString());
            };

            String contentType = switch (data.config().format()) {
                case JSON -> "text/json";
                case TTL -> "text/ttl";
                case HTML -> "text/html";
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
