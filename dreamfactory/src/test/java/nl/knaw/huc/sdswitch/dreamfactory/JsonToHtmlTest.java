package nl.knaw.huc.sdswitch.dreamfactory;

import net.sf.saxon.s9api.SaxonApiException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JsonToHtmlTest {
    private JsonToHtml toHtml;

    @Before
    public void init() throws SaxonApiException {
        toHtml = new JsonToHtml("src/test/resources/raa_xml2html.xsl");
    }

    @Test
    public void runTestJsonHtml() throws SaxonApiException {
        String json = "{ \"id\":1, \"voornaam\":\"Willem Frederik\" }";
        String expectedResult = getExpectedTestOne();
        String result = toHtml.toHtml(json);
        Assert.assertEquals(expectedResult, result);
    }

    private String getExpectedTestOne() {
        return """
                <!DOCTYPE HTML><html xmlns:js="http://www.w3.org/2005/xpath-functions">
                   <head>
                      <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
                      <title></title>
                   </head>
                   <style>
                                table,
                                td,
                                th {
                                border: 1px solid;
                                border-collapse: collapse;
                                }
                                td {
                                padding: 0 1ex 0 1ex;
                                }
                            </style>
                   <body>
                      <table>
                         <tbody>
                            <tr>
                               <th>Veld</th>
                               <th>Waarde</th>
                            </tr>
                            <tr>
                               <td>id</td>
                               <td>1</td>
                            </tr>
                            <tr>
                               <td>voornaam</td>
                               <td>Willem Frederik</td>
                            </tr>
                         </tbody>
                      </table>
                   </body>
                </html>""";
    }
}
