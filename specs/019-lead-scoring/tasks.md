# Tasks: 鏅鸿兘绾跨储璇勫垎涓庨攢鍞娴嬫牎鍑?
**Input**: Design documents from `/specs/019-lead-scoring/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/lead-scoring.md

**Tests**: 绔犵▼鍘熷垯鍥涜姹傛祴璇曞厛浜庡疄鐜帮紙绾⑩啋缁匡級锛屾湰鍔熻兘鍚悗绔崟鍏?闆嗘垚 + 鍓嶇娓叉煋娴嬭瘯銆?
## Phase 1: 鍩虹璁炬柦

- [x] T001 [P] 鍚庣锛欶lyway `backend/src/main/resources/db/migration/V43__lead_score_config.sql` 寤?`lead_score_config` 琛?+ 榛樿绉嶅瓙锛圫OURCE 鏉ユ簮 30 / INFO 淇℃伅瀹屾暣搴?30 / FOLLOWUP 璺熻繘娲昏穬搴?25 / FRESHNESS 浜掑姩鏃舵晥 15锛夈€?- [x] T002 [P] 鍚庣锛氭柊澧?`entity/LeadScoreConfig.java`锛坙ombok @TableName锛? `repository/LeadScoreConfigMapper.java`銆?
## Phase 2: 鍚庣娴嬭瘯鍏堣锛圱DD 绾級

- [x] T003 [P] [US1] 鍚庣锛氱紪鍐?`backend/src/test/java/com/crm/service/LeadScoreServiceTest.java` 鍗曞厓娴嬭瘯鈥斺€旇鐩栵細楂樺垎绾跨储锛圧EFERRAL+淇℃伅鍏?杩戞湡璺熻繘锛夈€佷綆鍒嗙嚎绱紙COLD_CALL+淇℃伅灏?鏃犺窡杩涳級銆佹棤淇℃伅鍩虹鍒嗐€佽窡杩涙椿璺冨害鍔犲垎銆侀槇鍊奸鑹层€傛鏃?LeadScoreService 鏈疄鐜帮紝娴嬭瘯缂栬瘧澶辫触锛堢孩锛夈€?- [x] T004 [P] [US2] 鍚庣锛氱紪鍐?`backend/src/test/java/com/crm/service/StageConversionServiceTest.java` 鍗曞厓娴嬭瘯鈥斺€旇鐩栵細鏍锋湰鍏呰冻鐢ㄥ巻鍙茶浆鍖栫巼銆佹牱鏈笉瓒冲洖閫€榛樿銆丆LOSED_WON=1.0 鍥哄畾銆傛鏃?StageConversionService 鏈疄鐜帮紝娴嬭瘯澶辫触锛堢孩锛夈€?
## Phase 3: 鍚庣瀹炵幇锛圲S1 绾跨储璇勫垎锛?
- [x] T005 [US1] 鍚庣锛氭柊澧?`service/LeadScoreService.java`鈥斺€旇閰嶇疆锛圫OURCE/INFO/FOLLOWUP/FRESHNESS 鍥涚淮搴︼級锛屾寜 research.md R1 璁＄畻 0-100 鍒嗗苟鍐欏洖 lead.score锛沗scoreAndUpdate(Lead)` 渚涜皟鐢ㄣ€傦紙渚濊禆 T001/T002锛?- [x] T006 [US1] 鍚庣锛歚LeadService.create()` 涓?`update()` 鍦ㄤ繚瀛樺悗璋冪敤 `LeadScoreService.scoreAndUpdate`锛沗FollowUpService.create()`锛堝惈 leadId 鏃讹級瑙﹀彂璇ョ嚎绱㈤噸绠椼€傦紙渚濊禆 T005锛?- [x] T007 [US1] 鍚庣锛歚LeadService.page()` 涓庣嚎绱㈡睜鏌ヨ鏀寔 `sortBy=score&order=desc` 榛樿璇勫垎闄嶅簭銆?
## Phase 4: 鍚庣瀹炵幇锛圲S2 棰勬祴鏍″噯锛?
- [x] T008 [US2] 鍚庣锛氭柊澧?`service/StageConversionService.java`鈥斺€斾粠 sales_opportunity 缁熻鍚勯樁娈佃浆鍖栫巼锛堟牱鏈?鈮?0 鐢ㄥ巻鍙诧紝鍚﹀垯鍥為€€榛樿锛夛紝CLOSED_WON=1.0/CLOSED_LOST=0.0 鍥哄畾锛岀粨鏋?Redis 缂撳瓨 5 鍒嗛挓銆?- [x] T009 [US2] 鍚庣锛歚DashboardStatsService.computeForecast()` 鏀圭敤 StageConversionService 姒傜巼锛沗ForecastItem` 鏂板 `probabilitySource`锛圚ISTORICAL/DEFAULT/FIXED锛夈€傦紙渚濊禆 T008锛?
## Phase 5: 鍓嶇

- [x] T010 [P] [US1] 鍓嶇锛歚types/lead.ts` 鐩稿叧绫诲瀷纭锛坰core 宸叉湁锛夛紱`pages/leads/LeadListPage.tsx` 鍒楄〃璇锋眰鍔?`sortBy=score&order=desc`锛岃瘎鍒?Tag 绾㈤粍缁垮睍绀猴紙闃堝€?40/70锛夈€?- [x] T011 [P] [US2] 鍓嶇锛歚types/stats.ts` ForecastItem 鍔?`probabilitySource`锛沗DashboardPage` 棰勬祴鍖哄潡灞曠ず"鍘嗗彶鏍″噯/榛樿"鏉ユ簮鏍囨敞銆?
## Phase 6: 楠岃瘉涓庢敹灏?
- [x] T012 鍚庣锛歚mvn test` 鍏ㄩ噺閫氳繃锛堟柊澧?LeadScoreServiceTest + StageConversionServiceTest + LeadScoringIT锛屼笉褰卞搷鏃㈡湁 208锛夈€?- [x] T013 鍓嶇锛歚pnpm run typecheck` + `lint` + `test` 鍏ㄩ噺閫氳繃銆?- [x] T014 [P] 鎵嬪姩鍐掔儫锛氬垱寤虹嚎绱㈣鑷姩璇勫垎涓庣孩榛勭豢鏍囪瘑锛涢椤甸娴嬭鏍″噯姒傜巼涓庢潵婧愭爣娉ㄣ€?
## Dependencies & Execution Order

- T001/T002 鍙苟琛岋紙鍩虹璁炬柦锛夈€?- T003/T004 鍙苟琛岋紝鍧囦负绾㈤樁娈碉紱渚濊禆 T001/T002銆?- T005 渚濊禆 T001/T002锛汿006 渚濊禆 T005锛汿007 渚濊禆 T006銆?- T008 渚濊禆鏃狅紱T009 渚濊禆 T008銆?- T010/T011 鍙苟琛屻€?- Phase 6 鍦ㄦ墍鏈夊疄鐜板畬鎴愬悗鎵ц銆?
## Notes

- 璇勫垎瑙勫垯寮曟搸锛堥潪 ML锛夛紝閰嶇疆瀛?lead_score_config 琛紝瀹炴椂璁＄畻鍐欏洖 lead.score銆?- 杞寲鐜囩粺璁′竴娆¤仛鍚?+ Redis 5 鍒嗛挓缂撳瓨锛孋LOSED_WON/LOST 鍥哄畾璇箟涓嶅彉銆?- 澶嶇敤 Lead.score 瀛楁锛?04锛夛紝鑷姩璇勫垎瑕嗙洊鎵嬪伐缂虹渷鍊笺€?