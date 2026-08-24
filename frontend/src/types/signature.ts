export interface SignatureRecord {
  id: number
  businessType: 'QUOTE' | 'CONTRACT'
  businessId: number
  signerId: number
  signerName?: string
  signedAt: string
  signatureImage: string
}
