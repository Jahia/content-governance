package org.jahia.community.contentgovernance.geo.servlet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlerControlTest {

    @Test
    @DisplayName("the control is the first agent of the list that answered 200, whatever the order of the answers")
    void control_isTheFirstAgentThatAnswered() {
        CrawlerCheckServlet.Control control = new CrawlerCheckServlet.Control();

        control.offer(2, 200, "third");
        control.offer(0, 403, "refused");
        control.offer(1, 200, "second");
        control.offer(3, 200, "fourth");

        assertThat(control.body()).isEqualTo("second");
    }

    @Test
    @DisplayName("no agent that answered 200 gives no control")
    void noAnswer_givesNoControl() {
        CrawlerCheckServlet.Control control = new CrawlerCheckServlet.Control();

        control.offer(0, 403, "refused");

        assertThat(control.body()).isNull();
    }
}
