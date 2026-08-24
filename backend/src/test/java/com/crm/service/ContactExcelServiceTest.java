package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.dto.customer.ImportResult;
import com.crm.entity.Contact;
import com.crm.entity.Customer;
import com.crm.repository.ContactMapper;
import com.crm.repository.CustomerMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** ContactExcelService 单元测试（024 T001）：客户匹配/校验/成功失败。 */
class ContactExcelServiceTest {

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, Customer.class);
    TableInfoHelper.initTableInfo(assistant, Contact.class);
  }

  private ContactMapper contactMapper;
  private CustomerMapper customerMapper;
  private AuditService auditService;
  private ContactExcelService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    contactMapper = mock(ContactMapper.class);
    customerMapper = mock(CustomerMapper.class);
    auditService = mock(AuditService.class);
    service = new ContactExcelService(contactMapper, customerMapper, auditService);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
    securityUtilMock
        .when(SecurityUtil::currentPrincipal)
        .thenReturn(new CrmPrincipal(1L, "admin", "ADMIN"));
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private byte[] buildXlsx(String[]... rows) throws Exception {
    try (Workbook wb = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = wb.createSheet("联系人");
      String[] headers = {"姓名", "客户名称", "职位", "电话", "邮箱", "角色", "备注"};
      Row h = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        h.createCell(i).setCellValue(headers[i]);
      }
      int idx = 1;
      for (String[] r : rows) {
        Row row = sheet.createRow(idx++);
        for (int i = 0; i < r.length; i++) {
          row.createCell(i).setCellValue(r[i]);
        }
      }
      wb.write(out);
      return out.toByteArray();
    }
  }

  private Customer customer(Long id, String name) {
    Customer c = new Customer();
    c.setId(id);
    c.setName(name);
    return c;
  }

  @Test
  @DisplayName("按客户名称匹配成功导入")
  void importValidRows() throws Exception {
    byte[] bytes =
        buildXlsx(new String[] {"张三", "Acme 科技", "CTO", "138", "a@b.com", "DECISION_MAKER", ""});
    when(customerMapper.selectList(any())).thenReturn(List.of(customer(10L, "Acme 科技")));

    ImportResult result = service.importContacts(new ByteArrayInputStream(bytes));

    assertThat(result.getSuccessCount()).isEqualTo(1);
    assertThat(result.getFailureCount()).isZero();
    verify(contactMapper).insert(any(Contact.class));
    verify(auditService).record("IMPORT", "CONTACT", null, "批量导入联系人：1 成功");
  }

  @Test
  @DisplayName("客户不存在失败；缺姓名失败")
  void invalidRowsFail() throws Exception {
    byte[] bytes =
        buildXlsx(
            new String[] {"张三", "不存在的公司", "", "", "", "", ""},
            new String[] {"", "Acme 科技", "", "", "", "", ""});
    when(customerMapper.selectList(any())).thenReturn(List.of());

    ImportResult result = service.importContacts(new ByteArrayInputStream(bytes));

    assertThat(result.getSuccessCount()).isZero();
    assertThat(result.getFailureCount()).isEqualTo(2);
    assertThat(result.getFailures().get(0).getMessage()).contains("客户");
    assertThat(result.getFailures().get(1).getMessage()).contains("姓名");
    verify(contactMapper, never()).insert(any());
  }

  @Test
  @DisplayName("非 xlsx 文件抛 IllegalArgumentException")
  void nonXlsxRejected() {
    assertThatThrownBy(() -> service.importContacts(new ByteArrayInputStream("bad".getBytes())))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
