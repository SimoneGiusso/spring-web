package org.simonegiusso.springweb.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.simonegiusso.springweb.support.ProductTestFactory.ALICE;
import static org.simonegiusso.springweb.support.ProductTestFactory.BOB;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.simonegiusso.springweb.support.BaseIT;
import org.simonegiusso.springweb.support.ProductTestFactory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

class ProductCountLoggerIT extends BaseIT {

    @Autowired
    private ProductTestFactory testData;

    @Autowired
    private ProductCountLogger countLogger;

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void captureLogsOfTheTask() {
        logs = new ListAppender<>();
        logs.start();
        logbackLogger().addAppender(logs);
    }

    @AfterEach
    void releaseLogsOfTheTask() {
        logbackLogger().detachAppender(logs);
    }

    @Test
    void givenProductsOfSeveralOwners_whenTheTaskRuns_thenLogTheTotalAcrossAllOfThem() {
        testData.insertAnEspressoMachineOwnedBy(ALICE);
        testData.insertAKeyboardOwnedBy(BOB);

        countLogger.logProductCount();

        assertThat(loggedMessages()).contains("Products stored: 2");
    }

    private List<String> loggedMessages() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static Logger logbackLogger() {
        return (Logger) LoggerFactory.getLogger(ProductCountLogger.class);
    }
}
