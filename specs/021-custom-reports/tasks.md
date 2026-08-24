# Tasks: 鑷畾涔夋姤琛?
**Input**: Design documents from `/specs/021-custom-reports/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/custom-reports.md

**Tests**: 绔犵▼鍘熷垯鍥涜姹傛祴璇曞厛浜庡疄鐜帮紙绾⑩啋缁匡級锛屾湰鍔熻兘鍚悗绔崟鍏?闆嗘垚 + 鍓嶇娓叉煋娴嬭瘯銆?
## Phase 1: 鍩虹璁炬柦

- [x] T001 [P] 鍚庣锛欶lyway `backend/src/main/resources/db/migration/V45__report_template.sql` 寤?`report_template` 琛紙P3 妯℃澘锛夈€?- [x] T002 [P] 鍚庣锛氭柊澧?`dto/report/ReportQuery.java`锛坉imension/metric/granularity/startDate/endDate/stageFilter锛夈€乣dto/report/ReportRow.java`銆乣dto/report/ReportResult.java`銆?
## Phase 2: 鍚庣娴嬭瘯鍏堣锛圱DD 绾級

- [x] T003 [P] [US1] 鍚庣锛氱紪鍐?`backend/src/test/java/com/crm/service/ReportServiceTest.java` 鍗曞厓娴嬭瘯鈥斺€旇鐩栵細鎸夐攢鍞紙created_by锛夎仛鍚堥噾棰濄€佹寜浜у搧锛坬uote_item锛夎仛鍚堛€佹寜鏉ユ簮锛坙ead.source锛夎仛鍚堟暟閲忋€佹椂闂寸矑搴︺€侀噾棰濆崰姣斾笌鎺掑簭銆傛鏃?ReportService 鏈疄鐜帮紝娴嬭瘯缂栬瘧澶辫触锛堢孩锛夈€?- [x] T004 [P] [US1] 鍚庣锛氱紪鍐?`backend/src/test/java/com/crm/integration/CustomReportsIT.java` 闆嗘垚娴嬭瘯鈥斺€擯OST /reports/query 鎸夐攢鍞仛鍚堣繑鍥炴纭紱闈炴硶缁村害/鏃堕棿杩斿洖 422锛汫ET /reports/export 杩斿洖 xlsx銆傛鏃舵帴鍙ｆ湭瀹炵幇锛屾祴璇曞け璐ワ紙绾級銆?
## Phase 3: 鍚庣瀹炵幇锛圲S1 鑱氬悎鏌ヨ锛?
- [x] T005 [US1] 鍚庣锛氭柊澧?`service/ReportService.java`鈥斺€旀寜缁村害锛圫ALES/PRODUCT/SOURCE/STAGE/TIME锛夎仛鍚堬紝鍐呭瓨 LinkedHashMap 鍒嗙粍锛岃緭鍑?rows锛堟寜閲戦闄嶅簭锛? 鍚堣 + 鍗犳瘮锛汼ALES 鐢ㄦ埛浠呮湰浜烘暟鎹紙created_by=褰撳墠鐢ㄦ埛锛夈€傦紙渚濊禆 T002锛?- [x] T006 [US1] 鍚庣锛氭柊澧?`controller/ReportController.java`鈥斺€擯OST /reports/query锛堟牎楠岀淮搴?鎸囨爣/鏃堕棿锛夛紱`GET /reports/export` 鐢?POI 鐢熸垚 xlsx銆傦紙渚濊禆 T005锛?
## Phase 4: 鍚庣瀹炵幇锛圲S2 瀵煎嚭 / P3 妯℃澘锛?
- [x] T007 [P] [US2] 鍚庣锛歚ReportController` 瀵煎嚭绔偣鐢?POI 鐢熸垚 xlsx锛堣〃澶?缁村害/鏁伴噺/閲戦/鍗犳瘮 + 鏁版嵁琛岋級锛屽搷搴旀祦涓嬭浇銆傦紙渚濊禆 T006锛?- [x] T008 [P] [US3] 鍚庣锛欶lyway V45 琛?+ `entity/ReportTemplate.java` + `ReportTemplateMapper` + `ReportTemplateService`锛堜繚瀛?鍒楄〃/鍒犻櫎/鎸夋ā鏉挎墽琛岋級锛汻eportController 妯℃澘 CRUD锛堜粎 ADMIN锛夈€傦紙渚濊禆 T001锛?
## Phase 5: 鍓嶇

- [x] T009 [P] [US1] 鍓嶇锛歚types/report.ts` + `services/reportService.ts`锛坬ueryReport/exportReport锛夈€?- [x] T010 [US1] 鍓嶇锛氭柊澧?`pages/reports/ReportCenterPage.tsx`鈥斺€旂淮搴?鎸囨爣/鏃堕棿鑼冨洿/绮掑害閫夋嫨 + 缁撴灉琛ㄦ牸锛堢淮搴﹀€?鏁伴噺/閲戦/鍗犳瘮 Progress锛? 瀵煎嚭鎸夐挳锛沗App.tsx` 娉ㄥ唽璺敱锛堟暟鎹垎鏋愬垎缁勶級銆傦紙渚濊禆 T009锛?
## Phase 6: 楠岃瘉涓庢敹灏?
- [x] T011 鍚庣锛歚mvn test` 鍏ㄩ噺閫氳繃锛堟柊澧?ReportServiceTest + CustomReportsIT锛屼笉褰卞搷鏃㈡湁 220锛夈€?- [x] T012 鍓嶇锛歚pnpm run typecheck` + `lint` + `test` 鍏ㄩ噺閫氳繃銆?- [x] T013 [P] 鎵嬪姩鍐掔儫锛氶€夋嫨"閿€鍞?璧㈠崟閲戦+鏈湀"鈫?琛ㄦ牸灞曠ず锛涘鍑?xlsx 鍐呭涓€鑷淬€?
## Dependencies & Execution Order

- T001/T002 鍙苟琛岋紙鍩虹璁炬柦锛夈€?- T003/T004 鍙苟琛岋紝鍧囦负绾㈤樁娈碉紱渚濊禆 T002銆?- T005 渚濊禆 T002锛汿006 渚濊禆 T005锛汿007 渚濊禆 T006锛汿008 渚濊禆 T001銆?- T009 渚濊禆鏃狅紱T010 渚濊禆 T009銆?- Phase 6 鍦ㄦ墍鏈夊疄鐜板畬鎴愬悗鎵ц銆?
## Notes

- 缁村害鑱氬悎鐢ㄧ幇鏈?Mapper + 鍐呭瓨鍒嗙粍锛堜竾绾ф暟鎹級锛屼笉寮曞叆 OLAP銆?- 瀵煎嚭澶嶇敤 POI锛?16 宸叉湁渚濊禆锛夈€?- 妯℃澘涓?P3 鍙€夊寮猴紝鏈湡瀹炵幇鍩虹淇濆瓨/鍔犺浇銆?- 鏁版嵁鏉冮檺锛歋ALES 浠呮湰浜猴紝ADMIN 鍏ㄩ噺銆?