package com.example.alertagent.api;

import com.example.alertagent.domain.AlertAnalysisRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AsyncAlertAnalysisContractTest {

    @Test
    void submissionEndpointReturnsAcceptedInsteadOfWaitingForTheModel() throws Exception {
        Method method = AlertAnalysisController.class.getMethod("analyze", AlertAnalysisRequest.class);
        ResponseStatus responseStatus = method.getAnnotation(ResponseStatus.class);

        assertThat(responseStatus).isNotNull();
        assertThat(responseStatus.value()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(method.getReturnType().getSimpleName()).isEqualTo("AlertAnalysisSubmissionResponse");
    }

    @Test
    void exposesAStatusQueryEndpoint() {
        assertThatCode(() -> AlertAnalysisController.class.getMethod("getAnalysisJob", String.class))
                .doesNotThrowAnyException();
    }

    @Test
    void includesElasticsearchPersistenceAndVirtualThreadConfiguration() {
        assertThatCode(() -> Class.forName("org.springframework.data.elasticsearch.core.ElasticsearchOperations"))
                .doesNotThrowAnyException();
        assertThatCode(() -> Class.forName("com.example.alertagent.config.AsyncAnalysisConfiguration"))
                .doesNotThrowAnyException();
    }
}
