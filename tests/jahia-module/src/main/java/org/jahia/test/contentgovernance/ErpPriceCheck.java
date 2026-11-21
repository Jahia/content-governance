package org.jahia.test.contentgovernance;

import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.check.spi.Rule;
import org.osgi.service.component.annotations.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A check that another module registers, as a customer would: a price typed in a text, such as
 * "12.50 EUR", breaks the rule acme.erpPrice, because prices come from the ERP.
 */
@Component(service = ContentCheck.class, property = ContentCheck.ID + "=acme.erpPrice")
public class ErpPriceCheck implements ContentCheck {

    private static final Pattern PRICE = Pattern.compile("\\d+[.,]\\d{2}\\s?(?:EUR|€)");

    @Override
    public Set<Scope> scopes() {
        return Set.of(Scope.VALUE);
    }

    @Override
    public List<Finding> check(CheckInput input, Rule rule) {
        List<Finding> found = new ArrayList<>();
        Matcher price = PRICE.matcher(input.text() == null ? "" : input.text());
        while (price.find()) {
            found.add(Finding.of(rule, price.group()));
        }
        return found;
    }
}
