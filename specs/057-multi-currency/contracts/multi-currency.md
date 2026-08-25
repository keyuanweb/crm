# 契约：多币种 /api/v1/currencies + /api/v1/product-prices

## 汇率管理（仅 ADMIN）

### GET /currencies

币种列表。**Response 200**

```json
{
  "items": [
    { "id": 1, "code": "CNY", "name": "人民币", "rate": 1, "isBase": true, "enabled": true },
    { "id": 2, "code": "USD", "name": "美元", "rate": 7.2, "isBase": false, "enabled": true }
  ],
  "total": 2
}
```

### POST /currencies

新增币种。**Body**: `{ "code": "USD", "name": "美元", "rate": 7.2 }`。**Response 201**。

### PUT /currencies/{id}

更新（汇率/名称/启用）。**Body**: 同 POST + version。基准 CNY 汇率不可改。

### DELETE /currencies/{id}

删除（基准不可删）。

## 折算（ADMIN + SALES）

### POST /currencies/convert

**Body**: `{ "amount": 100000, "fromCurrency": "CNY", "toCurrency": "USD" }`（amount 分）。

**Response 200**: `{ "amount": 100000, "fromCurrency": "CNY", "toCurrency": "USD", "convertedAmount": 13889 }`（折算后分，四舍五入）。

## 产品多币种价格（ADMIN + SALES）

### GET /products/{id}/prices

产品多币种价列表（含基准价与各币种折算价）。**Response 200**

```json
{
  "productId": 1, "basePrice": 100000,
  "prices": [
    { "currencyCode": "USD", "price": 14000, "converted": 13889, "configured": true }
  ]
}
```

### POST /products/{id}/prices

设置产品币种价。**Body**: `{ "currencyCode": "USD", "price": 14000 }`（upsert）。**Response 201**。

### DELETE /products/{id}/prices/{currencyCode}

删除产品币种价（回退汇率折算）。

## 错误码

| code | status | 含义 |
|---|---|---|
| CURRENCY_CODE_DUPLICATE | 409 | 币种代码已存在 |
| CURRENCY_BASE_IMMUTABLE | 422 | 基准币种不可修改/删除 |
| CURRENCY_NOT_FOUND | 404 | 币种不存在 |
| CURRENCY_RATE_INVALID | 422 | 汇率不合法（>0） |
| PRODUCT_CURRENCY_DUPLICATE | 409 | 产品币种价已存在 |
