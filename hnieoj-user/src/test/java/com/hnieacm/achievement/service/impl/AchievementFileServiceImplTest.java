package com.hnieacm.achievement.service.impl;

import com.hnieacm.achievement.properties.AchievementFileProperties;
import com.hnieacm.achievement.service.AchievementFileService;
import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/20
 * @Description: 成就附件本地读取回归：真实落盘后按字节读取，配置的相对 publicUrlPrefix 可精确剥离，
 * 缺失/外部 URL/路径穿越均被拒绝。
 */
class AchievementFileServiceImplTest {

    @TempDir
    Path uploadDir;

    private AchievementFileServiceImpl service;

    @BeforeEach
    void setUp() {
        AchievementFileProperties properties = new AchievementFileProperties();
        properties.setUploadDir(uploadDir.toString());
        service = new AchievementFileServiceImpl(properties);
    }

    @Test
    void storedLocalFileIsLoadedByteForByte() {
        byte[] content = "integration proof".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "proof.txt", "text/plain", content);

        String key = service.store("20230001", file);

        assertThat(key).doesNotContain("/").doesNotContain("\\");
        AchievementFileService.LocalFile loaded = service.loadLocal(key);
        assertThat(loaded.content()).isEqualTo(content);
        assertThat(loaded.filename()).isEqualTo(key);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "/files/achievements", "/files/achievements/",
            "files/achievements", "/", " /files/achievements/ "})
    void storedValueRoundTripsByteForByteForEveryConfiguredPrefix(String prefix) throws IOException {
        AchievementFileServiceImpl prefixed = serviceWithPrefix(prefix);
        byte[] content = ("roundtrip for prefix: " + prefix).getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "proof.txt", "text/plain", content);

        String stored = prefixed.store("20230001", file);

        String bareKey = singleStoredFile();
        assertThat(bareKey).doesNotContain("/").doesNotContain("\\");
        assertThat(stored).isEqualTo(joinPrefix(prefix, bareKey));
        // 裸 key 在配置前缀后仍可直接读取
        assertThat(prefixed.loadLocal(bareKey).content()).isEqualTo(content);
        assertThat(prefixed.loadLocal(bareKey).filename()).isEqualTo(bareKey);
        // 申请记录中的存储值剥离当前配置前缀后按字节返回原文件
        AchievementFileService.LocalFile loaded = prefixed.loadLocal(stored);
        assertThat(loaded.content()).isEqualTo(content);
        assertThat(loaded.filename()).isEqualTo(bareKey);
    }

    @Test
    void relativePrefixOnlyStripsExactBoundary() {
        AchievementFileServiceImpl prefixed = serviceWithPrefix("/files/achievements");

        assertBadRequest(prefixed, "/files/achievements2/key.txt");
        assertBadRequest(prefixed, "/other/key.txt");
        assertBadRequest(prefixed, "/files/achievements/../secret");
        assertBadRequest(prefixed, "/files/achievements/sub/key");
        assertBadRequest(prefixed, "/files/achievements/sub\\key.txt");
        assertBadRequest(prefixed, "/files/achievements/");
        assertBadRequest(prefixed, "/files/achievements");
        assertBadRequest(prefixed, "//files/achievements/key.txt");
        assertBadRequest(prefixed, "/files/achievements/key.txt/");
    }

    @Test
    void httpUrlsAreRejectedEvenWhenRelativePrefixConfigured() {
        AchievementFileServiceImpl prefixed = serviceWithPrefix("/files/achievements");

        assertBadRequest(prefixed, "https://cdn.example.com/proof.pdf");
        assertBadRequest(prefixed, "http://cdn.example.com/proof.pdf");
    }

    @Test
    void missingLocalFileReturnsNotFound() {
        assertThatThrownBy(() -> service.loadLocal("20230001_missing.txt"))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ResultCode.NOT_FOUND));
    }

    @Test
    void missingLocalFileUnderRelativePrefixReturnsNotFound() {
        AchievementFileServiceImpl prefixed = serviceWithPrefix("/files/achievements");

        assertThatThrownBy(() -> prefixed.loadLocal("/files/achievements/20230001_missing.txt"))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ResultCode.NOT_FOUND));
    }

    @Test
    void externalUrlsAreNotProxiedByServer() {
        assertBadRequest("https://cdn.example.com/proof.pdf");
        assertBadRequest("http://cdn.example.com/proof.pdf");
    }

    @Test
    void traversalKeysAreRejected() {
        assertBadRequest("../secret.txt");
        assertBadRequest("sub/proof.txt");
        assertBadRequest("sub\\proof.txt");
    }

    @Test
    void publicUrlPrefixOutputIsTreatedAsExternal() {
        AchievementFileProperties properties = new AchievementFileProperties();
        properties.setUploadDir(uploadDir.toString());
        properties.setPublicUrlPrefix("https://cdn.example.com/achievements");
        AchievementFileServiceImpl prefixed = new AchievementFileServiceImpl(properties);

        String url = prefixed.store("20230001",
                new MockMultipartFile("file", "proof.txt", "text/plain", new byte[]{1}));

        assertThat(url).startsWith("https://cdn.example.com/achievements/");
        assertThatThrownBy(() -> prefixed.loadLocal(url))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ResultCode.BAD_REQUEST));
    }

    private AchievementFileServiceImpl serviceWithPrefix(String prefix) {
        AchievementFileProperties properties = new AchievementFileProperties();
        properties.setUploadDir(uploadDir.toString());
        properties.setPublicUrlPrefix(prefix);
        return new AchievementFileServiceImpl(properties);
    }

    /**
     * 按 store 的落库契约还原存储值：空白前缀等同未配置，非空白前缀 trim 后拼接裸 key。
     */
    private static String joinPrefix(String prefix, String bareKey) {
        String trimmed = prefix == null ? null : prefix.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            return bareKey;
        }
        return trimmed.endsWith("/") ? trimmed + bareKey : trimmed + "/" + bareKey;
    }

    private String singleStoredFile() throws IOException {
        try (Stream<Path> files = Files.list(uploadDir)) {
            List<Path> storedFiles = files.toList();
            assertThat(storedFiles).hasSize(1);
            return storedFiles.get(0).getFileName().toString();
        }
    }

    private void assertBadRequest(String storedValue) {
        assertBadRequest(service, storedValue);
    }

    private void assertBadRequest(AchievementFileServiceImpl target, String storedValue) {
        assertThatThrownBy(() -> target.loadLocal(storedValue))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ResultCode.BAD_REQUEST));
    }
}
