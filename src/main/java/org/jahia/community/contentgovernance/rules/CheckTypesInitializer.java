package org.jahia.community.contentgovernance.rules;

import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.osgi.service.component.annotations.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code choicelist[contentRuleCheckTypes,resourceBundle]}: each {@link CheckType}, with the property
 * {@value #ADD_MIXIN} that names its mixin. jContent reads that property, and shows the fields of the mixin
 * when the editor picks the kind. The initializer {@code resourceBundle} then gives each value its label.
 */
@Component(service = ModuleChoiceListInitializer.class)
public class CheckTypesInitializer implements ModuleChoiceListInitializer {

    static final String KEY = "contentRuleCheckTypes";
    /** The property of a choice that jContent reads to add a mixin. */
    static final String ADD_MIXIN = "addMixin";

    private String initializerKey = KEY;

    @Override
    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition definition, String param,
                                                     List<ChoiceListValue> values, Locale locale,
                                                     Map<String, Object> context) {
        List<ChoiceListValue> choices = new ArrayList<>();
        for (CheckType type : CheckType.values()) {
            ChoiceListValue choice = new ChoiceListValue(type.name(), type.name());
            choice.addProperty(ADD_MIXIN, type.mixin());
            choices.add(choice);
        }
        return choices;
    }

    @Override
    public void setKey(String key) {
        this.initializerKey = key;
    }

    @Override
    public String getKey() {
        return initializerKey;
    }
}
