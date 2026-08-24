# 契约：电子签署 /quotes/{id}/sign + /contracts/{id}/sign

## 签署操作（ADMIN + SALES）

### POST /quotes/{id}/sign

对 APPROVED 报价单发起签署。

**Body**: `{ "signatureImage": "data:image/png;base64,..." }`（签名图 base64，必填，≤500KB）。

**Response 201**: 签署记录结构。

**规则**: 仅 APPROVED 可签（422 SIGNATURE_STATE_INVALID）；同一报价仅一次（409 SIGNATURE_ALREADY_SIGNED）。签后状态 SIGNED。

### POST /contracts/{id}/sign

对 APPROVED 合同发起签署。同报价规则；签后状态 SIGNED，之后可生效。

## 签署记录查询

### GET /quotes/{id}/signature

报价签署记录。**Response 200**: 签署记录或空。

### GET /contracts/{id}/signature

合同签署记录。

**Response 200**

```json
{
  "id": 1, "businessType": "CONTRACT", "businessId": 7,
  "signerId": 1, "signerName": "系统管理员",
  "signedAt": "2026-08-25T10:00:00",
  "signatureImage": "data:image/png;base64,..."
}
```

## 错误码

| code | status | 含义 |
|---|---|---|
| SIGNATURE_STATE_INVALID | 422 | 单据状态不允许签署（非 APPROVED） |
| SIGNATURE_ALREADY_SIGNED | 409 | 该单据已签署 |
| SIGNATURE_IMAGE_REQUIRED | 422 | 签名图不能为空 |
| SIGNATURE_IMAGE_TOO_LARGE | 422 | 签名图过大（>500KB） |
| SIGNATURE_RECORD_NOT_FOUND | 404 | 签署记录不存在 |
