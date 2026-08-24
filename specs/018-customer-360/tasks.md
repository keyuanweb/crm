# Tasks: 瀹㈡埛 360 鐢诲儚涓庡仴搴峰害璇勫垎

**Input**: Design documents from `/specs/018-customer-360/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/customer-360.md

**Tests**: 绔犵▼鍘熷垯鍥涜姹傛祴璇曞厛浜庡疄鐜帮紙绾⑩啋缁匡級锛屾湰鍔熻兘鍚悗绔崟鍏?闆嗘垚 + 鍓嶇娓叉煋娴嬭瘯銆?
## Phase 1: 鍩虹璁炬柦

- [x] T001 [P] 鍚庣锛欶lyway `backend/src/main/resources/db/migration/V42__health_score_config.sql` 寤?`health_score_config` 琛紙dimension_key/label/weight/params_json/enabled/sort_order锛? 5 鏉￠粯璁ょ瀛愶紙璺熻繘娲昏穬搴?30 / 鍥炴鍙婃椂鎬?25 / 宸ュ崟 20 / 鍚堜綔娣卞害 15 / 杩戞湡浜掑姩 10锛夈€?- [x] T002 [P] 鍚庣锛氭柊澧?`entity/HealthScoreConfig.java`锛坙ombok @TableName锛? `repository/HealthScoreConfigMapper.java`銆?- [x] T003 [P] 鍚庣锛氭柊澧?`dto/customer/HealthScoreDTO.java`锛坰core/level/deductions锛? `dto/customer/CustomerHealthBrief.java`锛堥璀﹂」锛? `dto/customer/Customer360Response.java`锛堣仛鍚?DTO锛夈€?
## Phase 2: 鍚庣娴嬭瘯鍏堣锛圱DD 绾級

- [x] T004 [P] [US2] 鍚庣锛氱紪鍐?`backend/src/test/java/com/crm/service/HealthScoreServiceTest.java` 鍗曞厓娴嬭瘯鈥斺€旇鐩栵細娲昏穬涓斿洖娆炬甯?鈫?楂樺垎缁胯壊锛涗箙鏈窡杩?閫炬湡鍥炴 鈫?浣庡垎绾㈣壊锛涙棤鏁版嵁 鈫?涓€у垎锛涘け鍒嗗師鍥犲垪琛ㄦ纭紱閰嶇疆缂哄け缁村害璺宠繃銆傛鏃?HealthScoreService 鏈疄鐜帮紝娴嬭瘯缂栬瘧澶辫触锛堢孩锛夈€?- [x] T005 [P] [US1] 鍚庣锛氱紪鍐?`backend/src/test/java/com/crm/integration/Customer360IT.java` 闆嗘垚娴嬭瘯鈥斺€斿垱寤哄鎴?璁㈠崟+鍚堝悓+宸ュ崟+璺熻繘鍚?GET /customers/{id} 杩斿洖鑱氬悎鏁版嵁锛坥rders/paymentSummaries/contracts/tickets/amountSummary/health锛夛紱GET /customers/health/at-risk 杩斿洖涔呮湭璺熻繘瀹㈡埛銆傛鏃惰仛鍚堟湭瀹炵幇锛屾祴璇曞け璐ワ紙绾級銆?
## Phase 3: 鍚庣瀹炵幇锛圲S1 瀹㈡埛 360 鑱氬悎锛?
- [x] T006 [US1] 鍚庣锛氭柊澧?`service/Customer360Service.java`鈥斺€旀寜 customerId 鑱氬悎璁㈠崟锛圫alesOrderMapper锛夈€佸洖娆捐鍒?璁板綍锛圥aymentPlanMapper/PaymentRecordMapper锛夈€佸悎鍚岋紙ContractMapper锛夈€佸伐鍗曪紙TicketMapper锛夛紝鎵归噺瑁呴厤璁＄畻閲戦姹囨€伙紙totalOrder/paid/dueOverdue锛夛紝鏃?N+1銆?- [x] T007 [US1] 鍚庣锛歚service/CustomerService.java` 鐨?`detail()` 缁勫悎 Customer360Service 缁撴灉锛堣鍗?鍥炴/鍚堝悓/宸ュ崟/閲戦姹囨€伙級锛屾墿灞?CustomerDetailResponse 鎴栬繑鍥?Customer360Response銆傦紙渚濊禆 T006锛?
## Phase 4: 鍚庣瀹炵幇锛圲S2 鍋ュ悍搴﹁瘎鍒嗭級

- [x] T008 [P] [US2] 鍚庣锛氭柊澧?`service/HealthScoreService.java`鈥斺€旇鍙?HealthScoreConfig锛堝惎鐢ㄧ淮搴︼級锛屾寜 research.md R2 瑙勫垯璁＄畻 0-100 鍒嗭紙璺熻繘娲昏穬搴?鍥炴鍙婃椂鎬?宸ュ崟/鍚堜綔娣卞害/杩戞湡浜掑姩锛夛紝鏄犲皠绾㈤粍缁匡紙<60/60-79/鈮?0锛夛紝杈撳嚭澶卞垎鍘熷洜鍒楄〃锛涜绠楄 DEBUG 鏃ュ織銆傦紙渚濊禆 T001/T002锛?- [x] T009 [US2] 鍚庣锛歚CustomerController` 鍦?`GET /customers/{id}` 鍝嶅簲涓姞鍏?`health`锛堣皟鐢?HealthScoreService锛夛紱鏂板 `GET /customers/health/at-risk`锛?N 澶╂棤璺熻繘涓旀棤鏂拌鍗曪紝鎸夊仴搴峰害鍗囧簭锛屼粎褰撳墠鐢ㄦ埛鍙闂鎴凤級銆傦紙渚濊禆 T007/T008锛?
## Phase 5: 鍓嶇娴嬭瘯鍏堣锛圱DD 绾級

- [x] T010 [P] [US1] 鍓嶇锛氭柊澧?`frontend/src/pages/customers/CustomerDetailPage.render.test.tsx`鈥斺€攎ock customerService锛屾覆鏌撹鎯呴〉楠岃瘉 360 Tabs锛堣鍗?鍚堝悓/宸ュ崟锛変笌鍋ュ悍搴﹁瘎鍒嗗崱锛堝垎鏁?棰滆壊锛夊嚭鐜帮紱鏃犳暟鎹椂涓嶅穿婧冦€?
## Phase 6: 鍓嶇瀹炵幇

- [x] T011 [P] [US1] 鍓嶇锛歚types/customer.ts` 鏂板 Customer360Response/HealthScoreDTO/CustomerHealthBrief 绫诲瀷锛沗services/customerService.ts` 鏂板 `fetchCustomer360`/`fetchAtRiskCustomers`銆?- [x] T012 [US1] 鍓嶇锛歚pages/customers/CustomerDetailPage.tsx` 鏂板"瀹㈡埛 360"Tabs锛堟瑙?璁㈠崟鍥炴/鍚堝悓/宸ュ崟锛夛紝姒傝鍚仴搴峰害璇勫垎鍗★紙鍒嗘暟+棰滆壊 Tag+澶卞垎鍘熷洜锛? 閲戦姹囨€?Statistic銆傦紙渚濊禆 T011锛?- [x] T013 [US2] 鍓嶇锛氭柊澧?`pages/customers/AtRiskCustomersPage.tsx`鈥斺€旈璀﹀垪琛ㄨ〃鏍硷紙瀹㈡埛/鍋ュ悍搴?鏈€杩戣窡杩?鏃犳椿鍔ㄥぉ鏁?璐熻矗浜猴級+ 涓€閿窡杩涘脊绐楋紙澶嶇敤璺熻繘琛ㄥ崟锛夛紱`App.tsx` 娉ㄥ唽璺敱锛堟暟鎹垎鏋愬垎缁勶級銆?
## Phase 7: 楠岃瘉涓庢敹灏?
- [x] T014 鍚庣锛歚mvn test` 鍏ㄩ噺閫氳繃锛堟柊澧?HealthScoreServiceTest + Customer360IT锛屼笉褰卞搷鏃㈡湁 203 娴嬭瘯锛夈€?- [x] T015 鍓嶇锛歚pnpm run typecheck` + `lint` + `test` 鍏ㄩ噺閫氳繃锛堝惈鏂板 CustomerDetailPage 娓叉煋娴嬭瘯锛夈€?- [x] T016 [P] 鎵嬪姩鍐掔儫锛氬鎴疯鎯呴〉鍙 360 Tabs 涓庡仴搴峰害璇勫垎锛涢璀﹂〉鍙涔呮湭璺熻繘瀹㈡埛骞跺彲鍙戣捣璺熻繘銆?
## Dependencies & Execution Order

- T001/T002/T003 鍙苟琛岋紙鍩虹璁炬柦锛夈€?- T004锛堣瘎鍒嗘祴璇曪級/ T005锛堣仛鍚堥泦鎴愭祴璇曪級鍙苟琛岋紝鍧囦负绾㈤樁娈碉紱渚濊禆 T001-T003銆?- T006 渚濊禆 T001/T002/T003锛汿007 渚濊禆 T006銆?- T008 渚濊禆 T001/T002锛汿009 渚濊禆 T007/T008銆?- T010锛堝墠绔祴璇曪級鍙苟琛岋紱T011 渚濊禆鏃狅紱T012 渚濊禆 T011锛汿013 渚濊禆 T011銆?- Phase 7 鍦ㄦ墍鏈夊疄鐜板畬鎴愬悗鎵ц銆?
## Notes

- 璇勫垎绾鍒欏紩鎿庯紙闈?ML锛夛紝閰嶇疆瀛?health_score_config 琛紝瀹炴椂璁＄畻涓嶈惤搴撱€?- 鑱氬悎澶嶇敤鐜版湁 Mapper 鎵归噺瑁呴厤锛岄伩鍏?N+1锛堢珷绋嬪師鍒欎簲锛夈€?- 棰勮澶嶇敤鐜版湁璺熻繘鎺ュ彛锛?01锛変笌鏁版嵁鏉冮檺锛?12锛夈€?