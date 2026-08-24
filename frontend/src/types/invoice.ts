/** 发票（038）。 */
export interface Invoice {
  id: number
  orderId: number
  orderNo?: string
  customerName?: string
  invoiceNo: string
  title: string
  taxNo?: string
  amount: number
  invoiceType: string
  status: string
  voidReason?: string
  issuedAt?: string
  voidedAt?: string
}

export interface InvoicePayload {
  orderId: number
  title: string
  taxNo?: string
  amount: number
  invoiceType: string
}

export interface InvoiceStats {
  totalInvoiceAmount: number
  totalOrderAmount: number
  invoiceRate: number
  byOrder: { orderId: number; orderNo: string; invoiced: number; orderAmount: number; rate: number }[]
}
