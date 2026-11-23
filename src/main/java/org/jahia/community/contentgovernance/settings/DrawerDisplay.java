package org.jahia.community.contentgovernance.settings;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * What the drawer shows to every editor of the server, beyond the switches of a site.
 *
 * Edit digital-factory-data/karaf/etc/org.jahia.community.contentgovernance.drawer.cfg at runtime.
 * Each element stays hidden until its key is {@code true}; editors see a change the next time they
 * open the drawer.
 */
@Component(
        service = DrawerDisplay.class,
        configurationPid = DrawerDisplay.PID,
        property = {
                "service.description=Content Governance - drawer display",
                "service.vendor=Jahia Solutions Group SA"
        },
        immediate = true)
public class DrawerDisplay {

    static final String PID = "org.jahia.community.contentgovernance.drawer";
    static final String SHOW_FIX_FIRST = "SHOW_FIX_FIRST";
    static final String SHOW_PREVIEW_BUTTON = "SHOW_PREVIEW_BUTTON";

    /**
     * The elements of the drawer that the cfg shows.
     *
     * @param fixFirst      the "Fix first" list under the tab bar
     * @param previewButton the "Show preview" button of Page quality
     */
    public record Shown(boolean fixFirst, boolean previewButton) {

        static Shown from(Map<String, ?> properties) {
            return new Shown(isTrue(properties, SHOW_FIX_FIRST), isTrue(properties, SHOW_PREVIEW_BUTTON));
        }

        private static boolean isTrue(Map<String, ?> properties, String key) {
            Object value = properties.get(key);
            return value != null && Boolean.parseBoolean(String.valueOf(value).trim());
        }
    }

    private final AtomicReference<Shown> shown = new AtomicReference<>(Shown.from(Map.of()));

    @Activate
    @Modified
    protected void activate(Map<String, Object> properties) {
        shown.set(Shown.from(properties));
    }

    /** @return the elements that the cfg shows, as it was last read */
    public Shown shown() {
        return shown.get();
    }
}
