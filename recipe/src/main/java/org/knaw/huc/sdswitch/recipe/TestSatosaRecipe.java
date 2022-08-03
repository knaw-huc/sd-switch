package org.knaw.huc.sdswitch.recipe;

import net.sf.saxon.s9api.XdmItem;
import org.knaw.huc.sdswitch.server.recipe.Recipe;
import org.knaw.huc.sdswitch.server.recipe.RecipeData;
import org.knaw.huc.sdswitch.server.security.data.User;
import org.knaw.huc.sdswitch.server.recipe.RecipeResponse;

import java.util.Set;

public class TestSatosaRecipe implements Recipe<Void> {

    @Override
    public Void parseConfig(XdmItem config, XdmItem parentConfig, Set<String> pathParams) {
        return null;
    }

    @Override
    public RecipeResponse withData(RecipeData<Void> data) {
        if (data.context().attribute("user") instanceof User user) {
            return RecipeResponse.withBody("Hello " + user.getUserInfo().claims().get("nickname") + "!", "text/plain");
        }

        return RecipeResponse.withBody("Hello anonymous user!", "text/plain");
    }
}
