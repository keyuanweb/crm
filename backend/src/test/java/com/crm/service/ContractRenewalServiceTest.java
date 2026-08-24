package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.entity.Contract;
import com.crm.entity.Customer;
import com.crm.repository.ContractMapper;
import com.crm.repository.CustomerMapper;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ContractRenewalService 单元测试（046 T009）：到期归类/分组/去向装配。 */
class ContractRenewalServiceTest {

  private ContractMapper contractMapper;
  private CustomerMapper customerMapper;
  private ContractRenewalService service;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, Contract.class);
    TableInfoHelper.initTableInfo(assistant, Customer.class);
  }

  @BeforeEach
  void setUp() {
    contractMapper = mock(ContractMapper.class);
    customerMapper = mock(CustomerMapper.class);
    service = new ContractRenewalService(contractMapper, customerMapper);
  }

  private Contract contract(Long id, String no, LocalDate endDate, Long renewedFromId) {
    Contract c = new Contract();
    c.setId(id);
    c.setContractNo(no);
    c.setTitle("合同" + no);
    c.setCustomerId(1L);
    c.setAmount(1000L);
    c.setEndDate(endDate);
    c.setStatus("EFFECTIVE");
    c.setRenewedFromId(renewedFromId);
    return c;
  }

  private com.baomidou.mybatisplus.extension.plugins.pagination.Page<Contract> pageOf(
      List<Contract> list) {
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<Contract> p =
        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
    p.setRecords(list);
    p.setTotal(list.size());
    return p;
  }

  @Test
  @DisplayName("即将到期：endDate 在 90 天内且未过期")
  void expiringSoon() {
    when(contractMapper.selectPage(any(), any()))
        .thenReturn(
            pageOf(
                List.of(
                    contract(1L, "A", LocalDate.now().plusDays(30), null), // 即将到期
                    contract(2L, "B", LocalDate.now().plusDays(200), null)))); // 远期
    when(contractMapper.selectList(any())).thenReturn(List.of());
    when(customerMapper.selectBatchIds(any())).thenReturn(List.of());

    var result = service.overview("EXPIRING_SOON", null, 1, 20);

    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getItems().get(0).getContractNo()).isEqualTo("A");
  }

  @Test
  @DisplayName("已到期未续：endDate 已过且无续约去向")
  void expiredUnrenewed() {
    when(contractMapper.selectPage(any(), any()))
        .thenReturn(
            pageOf(
                List.of(
                    contract(1L, "A", LocalDate.now().minusDays(5), null), // 已到期未续
                    contract(2L, "B", LocalDate.now().minusDays(3), null))));
    // B 有续约去向（另一合同 renewedFromId=2）
    Contract renewal = contract(10L, "R", LocalDate.now().plusDays(300), 2L);
    when(contractMapper.selectList(any())).thenReturn(List.of(renewal));
    when(customerMapper.selectBatchIds(any())).thenReturn(List.of());

    var result = service.overview("EXPIRED_UNRENEWED", null, 1, 20);

    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getItems().get(0).getContractNo()).isEqualTo("A");
  }

  @Test
  @DisplayName("已续约：存在续约去向")
  void renewed() {
    when(contractMapper.selectPage(any(), any()))
        .thenReturn(
            pageOf(
                List.of(
                    contract(1L, "A", LocalDate.now().plusDays(10), null),
                    contract(2L, "B", LocalDate.now().plusDays(10), null))));
    Contract renewal = contract(10L, "R", LocalDate.now().plusDays(300), 2L);
    when(contractMapper.selectList(any())).thenReturn(List.of(renewal));
    when(customerMapper.selectBatchIds(any())).thenReturn(List.of());

    var result = service.overview("RENEWED", null, 1, 20);

    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getItems().get(0).getContractNo()).isEqualTo("B");
    assertThat(result.getItems().get(0).getRenewedBy()).hasSize(1);
  }

  @Test
  @DisplayName("非法分组 → 422")
  void invalidGroupThrows() {
    assertThatThrownBy(() -> service.overview("BAD", null, 1, 20))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CONTRACT_RENEWAL_GROUP_INVALID);
  }
}
