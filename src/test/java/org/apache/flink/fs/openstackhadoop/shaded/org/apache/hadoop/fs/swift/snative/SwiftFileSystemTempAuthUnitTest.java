package org.apache.flink.fs.openstackhadoop.shaded.org.apache.hadoop.fs.swift.snative;


import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.headRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.fs.FSDataInputStream;
import org.apache.flink.core.fs.FSDataOutputStream;
import org.apache.flink.core.fs.FileStatus;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.core.fs.Path;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.base.Charsets;

@RunWith(JUnit4.class)
public class SwiftFileSystemTempAuthUnitTest {
    public static final String AUTH_PATH = "/auth/v1.0";
    public static final String USERNAME = "user";
    public static final String PASSWORD = "password";
    public static final String CONTAINER_NAME = "updater";
    public static final String SWIFT_URI = "swift://" + CONTAINER_NAME + ".test-service/";
    public static final String AUTH_TOKEN = "auth token";
    public static final String STORAGE_TOKEN = "storage token";
    public static final String STORAGE_PATH = "/v1/AUTH_service";
    public static final String TEST_CONTENT = "test content";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().dynamicPort());
    private FileSystem fileSystem;
    public static final String FILE_PATH = "/checkpoint";
    public static final String FULL_URL = STORAGE_PATH + "/" + CONTAINER_NAME + FILE_PATH;

    @Before
    public void setUp() throws IOException {
        stubAuthResponse();
        stubPathProbe("/", HttpStatus.SC_OK);
        SwiftFileSystemFactoryTempAuth swiftFileSystemFactory = new SwiftFileSystemFactoryTempAuth();
        Configuration config = new Configuration();
        config.setString("swift.service.test-service.auth.url", wireMockRule.baseUrl() + AUTH_PATH);
        config.setString("swift.service.test-service.username", USERNAME);
        config.setString("swift.service.test-service.apikey", PASSWORD);
        swiftFileSystemFactory.configure(config);
        URI uri = URI.create(SWIFT_URI);
        fileSystem = swiftFileSystemFactory.create(uri);
    }

    @Test
    public void testCreate() throws IOException {
        stubPathProbe(FILE_PATH, HttpStatus.SC_NOT_FOUND);
        stubObjectCreate(FILE_PATH);
        stubObjectSearch(FILE_PATH.substring(1), "");

        FSDataOutputStream outputStream = fileSystem.create(new Path(FILE_PATH), FileSystem.WriteMode.OVERWRITE);
        verify(headRequestedFor(urlEqualTo(FULL_URL))
                .withHeader("x-auth-token", equalTo(AUTH_TOKEN)));

        outputStream.write(TEST_CONTENT.getBytes(Charsets.UTF_8));
        outputStream.close();
        verify(putRequestedFor(urlEqualTo(FULL_URL))
                .withRequestBody(equalTo(TEST_CONTENT))
                .withHeader("x-auth-token", equalTo(AUTH_TOKEN)
                ));
    }

    @Test
    public void testRead() throws IOException {
        stubObjectGet(FILE_PATH, TEST_CONTENT);

        FSDataInputStream inputStream = fileSystem.open(new Path(FILE_PATH));
        String content = new BufferedReader(new InputStreamReader(inputStream, Charsets.UTF_8)).readLine();

        assertThat(content).isEqualTo(TEST_CONTENT);
    }

    @Test
    public void testList() throws IOException {
        stubObjectSearch(FILE_PATH.substring(1), "&delimiter=/");
        FileStatus[] fileStatuses = fileSystem.listStatus(new Path(FILE_PATH));
        assertThat(fileStatuses).hasSize(1);
        assertThat(fileStatuses[0])
                .extracting(fileStatus -> fileStatus.getPath().toString())
                .isEqualTo(SWIFT_URI + FILE_PATH.substring(1));
    }

    @Test
    public void testDelete() throws IOException {
        stubPathProbe(FILE_PATH, HttpStatus.SC_OK);
        stubObjectSearch(FILE_PATH.substring(1), "");
        stubObjectDelete(FILE_PATH);
        Path path = new Path(FILE_PATH);
        fileSystem.delete(path, false);
        verify(deleteRequestedFor(urlEqualTo(FULL_URL))
                .withHeader("x-auth-token", equalTo(AUTH_TOKEN)
                ));
    }

    @Test
    public void testMkdirs() throws IOException {
        stubObjectCreate(FILE_PATH);
        stubObjectCreate(FILE_PATH + "/subdir");
        stubPathProbe(FILE_PATH, HttpStatus.SC_NOT_FOUND);
        stubPathProbe(FILE_PATH + "/subdir", HttpStatus.SC_NOT_FOUND);
        fileSystem.mkdirs(new Path(FILE_PATH + "/subdir"));
        verifyEmptyPut(FULL_URL);
        verifyEmptyPut(FULL_URL + "/subdir");
    }

    private void verifyEmptyPut(String testUrl) {
        verify(putRequestedFor(urlEqualTo(testUrl))
                .withHeader("x-auth-token", equalTo(AUTH_TOKEN)
                ));
    }

    private void stubObjectGet(String filePath, String content) {
        stubFor(get(urlEqualTo(STORAGE_PATH + "/" + CONTAINER_NAME + filePath))
                .withHeader("x-auth-token", equalTo(AUTH_TOKEN))
                .willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(content))
        );
    }

    private void stubObjectCreate(String filePath) {
        stubFor(put(urlEqualTo(STORAGE_PATH + "/" + CONTAINER_NAME + filePath))
                .withHeader("x-auth-token", equalTo(AUTH_TOKEN))
                .willReturn(aResponse().withStatus(HttpStatus.SC_CREATED)
                )
        );
    }

    private void stubObjectDelete(String filePath) {
        stubFor(delete(urlEqualTo(STORAGE_PATH + "/" + CONTAINER_NAME + filePath))
                .withHeader("x-auth-token", equalTo(AUTH_TOKEN))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)
                )
        );
    }

    private void stubObjectSearch(String filePath, String additionalQuery) {
        String content = "[\n" +
                "    {\n" +
                "        \"hash\": \"451e372e48e0f6b1114fa0724aa79fa1\",\n" +
                "        \"last_modified\": \"2020-01-15T16:41:49.390270\",\n" +
                "        \"bytes\": 14,\n" +
                "        \"name\": \"checkpoint\",\n" +
                "        \"content_type\": \"application/octet-stream\"\n" +
                "    }\n " +
                "]";
        stubFor(get(urlEqualTo(STORAGE_PATH + "/" + CONTAINER_NAME + "/?prefix=" + filePath + "/&format=json" + additionalQuery))
                .withHeader("x-auth-token", equalTo(AUTH_TOKEN))
                .willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(content))

        );
    }

    private void stubPathProbe(String filePath, int status) {
        stubFor(head(urlEqualTo(STORAGE_PATH + "/" + CONTAINER_NAME + filePath))
                .withHeader("x-auth-token", equalTo(AUTH_TOKEN))
                .willReturn(aResponse().withStatus(status)
                )
        );
    }

    private void stubAuthResponse() {
        stubFor(get(urlEqualTo(AUTH_PATH))
                .withHeader("X-Storage-User", equalTo(USERNAME))
                .withHeader("X-Storage-Pass", equalTo(PASSWORD))
                .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("x-storage-url", wireMockRule.baseUrl() + STORAGE_PATH)
                                .withHeader("x-auth-token-expires", "604799")
                                .withHeader("x-auth-token", AUTH_TOKEN)
                        .withHeader("content-type", "text/html; charset=UTF-8")
                                 .withHeader("x-storage-token", STORAGE_TOKEN)
                )
        );
    }
}
