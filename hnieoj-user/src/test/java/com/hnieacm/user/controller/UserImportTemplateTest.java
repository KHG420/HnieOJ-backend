package com.hnieacm.user.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hnieacm.common.constant.PermissionConstant;
import com.hnieacm.user.service.UserManageService;
import com.hnieacm.user.service.impl.UserImportServiceImpl;
import com.hnieacm.user.vo.CreateUserVo;
import com.hnieacm.user.vo.UserImportResultVo;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: HaoRan_Lyu
 * @Description: 用户导入模板接口回归：GET /api/users/import/template 保持 USER_MANAGE 管理权限，
 * 返回的模板由 UserImportServiceImpl.buildTemplate() 生成，必须包含导入器要求的 9 个表头，
 * 且同一份字节可直接交给当前 importUsers 解析导入。
 */
class UserImportTemplateTest {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private static final List<String> IMPORT_HEADERS = List.of(
            "uid", "username", "email", "password", "phone", "avatar", "collegeId", "classId", "grade");

    @Test
    void endpointIsDeclaredAsGetUnderUserManageAndKeepsUserManagePermission() throws Exception {
        RequestMapping base = UserManageController.class.getAnnotation(RequestMapping.class);
        assertThat(base).isNotNull();
        assertThat(base.value()).containsExactly("/api/users");

        SaCheckPermission permission = UserManageController.class.getAnnotation(SaCheckPermission.class);
        assertThat(permission).isNotNull();
        assertThat(permission.value()).containsExactly(PermissionConstant.USER_MANAGE);

        Method method = UserManageController.class.getMethod("downloadImportTemplate");
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/import/template");
    }

    @Test
    void downloadedTemplateCarriesAllImporterHeadersAndCanBeImported() throws Exception {
        UserManageService userManageService = mock(UserManageService.class);
        when(userManageService.createUser(any()))
                .thenReturn(new CreateUserVo("20220001", "HnieOJ@123456"));
        UserImportServiceImpl userImportService = new UserImportServiceImpl(userManageService);

        ResponseEntity<byte[]> response = new UserManageController(null, userImportService, null, null, null)
                .downloadImportTemplate();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(XLSX);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("user-import-template.xlsx");

        byte[] workbook = response.getBody();
        assertThat(workbook).isNotNull();

        Map<String, byte[]> parts = readZip(new ByteArrayInputStream(workbook));
        assertThat(parts).containsKeys(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/worksheets/sheet1.xml",
                "xl/sharedStrings.xml");

        assertThat(readFirstRowHeaders(parts)).containsExactlyElementsOf(IMPORT_HEADERS);

        // The downloaded bytes must be accepted by the current importer (real POI parser).
        MockMultipartFile file = new MockMultipartFile(
                "file", "user-import-template.xlsx", XLSX.toString(), workbook);
        UserImportResultVo result = userImportService.importUsers(file);

        verify(userManageService).createUser(any());
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailures()).isEmpty();
        assertThat(result.getCreatedUsers()).singleElement()
                .satisfies(item -> assertThat(item.getUid()).isEqualTo("20220001"));
    }

    private static Map<String, byte[]> readZip(InputStream input) throws Exception {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                parts.put(entry.getName(), zip.readAllBytes());
            }
        }
        return parts;
    }

    private static List<String> readFirstRowHeaders(Map<String, byte[]> parts) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        Document shared = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(parts.get("xl/sharedStrings.xml")));
        NodeList items = shared.getElementsByTagNameNS("*", "si");
        List<String> sharedStrings = new ArrayList<>();
        for (int i = 0; i < items.getLength(); i++) {
            sharedStrings.add(items.item(i).getTextContent());
        }

        Document sheet = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(parts.get("xl/worksheets/sheet1.xml")));
        NodeList rows = sheet.getElementsByTagNameNS("*", "row");
        assertThat(rows.getLength()).isGreaterThanOrEqualTo(1);

        NodeList cells = ((Element) rows.item(0)).getElementsByTagNameNS("*", "c");
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < cells.getLength(); i++) {
            Element cell = (Element) cells.item(i);
            assertThat(cell.getAttribute("t")).isEqualTo("s");
            Node value = cell.getElementsByTagNameNS("*", "v").item(0);
            headers.add(sharedStrings.get(Integer.parseInt(value.getTextContent())));
        }
        return headers;
    }
}
