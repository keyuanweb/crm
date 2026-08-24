package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.dto.tag.TagRequest;
import com.crm.dto.tag.TagResponse;
import com.crm.entity.CustomerTag;
import com.crm.entity.Tag;
import com.crm.repository.CustomerMapper;
import com.crm.repository.CustomerTagMapper;
import com.crm.repository.TagMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** TagService 单元测试（031 T004）。 */
class TagServiceTest {

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, Tag.class);
    TableInfoHelper.initTableInfo(assistant, CustomerTag.class);
  }

  private TagMapper tagMapper;
  private CustomerTagMapper customerTagMapper;
  private CustomerMapper customerMapper;
  private AuditService auditService;
  private TagService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    tagMapper = mock(TagMapper.class);
    customerTagMapper = mock(CustomerTagMapper.class);
    customerMapper = mock(CustomerMapper.class);
    auditService = mock(AuditService.class);
    service =
        new TagService(
            tagMapper,
            customerTagMapper,
            customerMapper,
            auditService,
            mock(WorkflowEventPublisher.class));
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

  @Test
  @DisplayName("创建标签：名称重复拒绝")
  void createDuplicateRejected() {
    when(tagMapper.selectCount(any())).thenReturn(1L);
    TagRequest req = new TagRequest();
    req.setName("VIP");
    req.setEntityType("CUSTOMER");

    assertThatThrownBy(() -> service.create(req)).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("创建标签成功")
  void createSuccess() {
    when(tagMapper.selectCount(any())).thenReturn(0L);
    TagRequest req = new TagRequest();
    req.setName("VIP");
    req.setColor("red");
    req.setEntityType("CUSTOMER");

    TagResponse resp = service.create(req);

    assertThat(resp.getName()).isEqualTo("VIP");
    verify(tagMapper).insert(any(Tag.class));
  }

  @Test
  @DisplayName("删除标签级联清关联")
  void deleteCascades() {
    when(tagMapper.selectById(1L)).thenReturn(tag(1L, "VIP"));

    service.delete(1L);

    verify(customerTagMapper).delete(any());
    verify(tagMapper).deleteById(1L);
  }

  @Test
  @DisplayName("客户打标：覆盖式清后插")
  void setCustomerTagsReplaces() {
    when(customerMapper.selectById(9L)).thenReturn(new com.crm.entity.Customer());

    service.setCustomerTags(9L, List.of(1L, 2L));

    verify(customerTagMapper).delete(any());
    verify(customerTagMapper, Mockito.times(2)).insert(any(CustomerTag.class));
  }

  @Test
  @DisplayName("按标签名取客户 id")
  void customerIdsByTagNames() {
    Tag t1 = tag(1L, "VIP");
    Tag t2 = tag(2L, "重点");
    when(tagMapper.selectList(any())).thenReturn(List.of(t1, t2));
    CustomerTag ct1 = new CustomerTag();
    ct1.setCustomerId(10L);
    ct1.setTagId(1L);
    CustomerTag ct2 = new CustomerTag();
    ct2.setCustomerId(11L);
    ct2.setTagId(2L);
    when(customerTagMapper.selectList(any())).thenReturn(List.of(ct1, ct2));

    List<Long> ids = service.customerIdsByTagNames(List.of("VIP", "重点"));

    assertThat(ids).containsExactlyInAnyOrder(10L, 11L);
  }

  private Tag tag(Long id, String name) {
    Tag t = new Tag();
    t.setId(id);
    t.setName(name);
    t.setEntityType("CUSTOMER");
    return t;
  }
}
