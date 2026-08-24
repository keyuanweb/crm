import { apiClient } from './apiClient'
import type { SignatureRecord } from '../types/signature'

export async function signQuote(id: number, signatureImage: string): Promise<SignatureRecord> {
  const { data } = await apiClient.post(`/quotes/${id}/sign`, { signatureImage })
  return data.data as SignatureRecord
}

export async function fetchQuoteSignature(id: number): Promise<SignatureRecord | null> {
  const { data } = await apiClient.get(`/quotes/${id}/signature`)
  return data.data as SignatureRecord | null
}

export async function signContract(id: number, signatureImage: string): Promise<SignatureRecord> {
  const { data } = await apiClient.post(`/contracts/${id}/sign`, { signatureImage })
  return data.data as SignatureRecord
}

export async function fetchContractSignature(id: number): Promise<SignatureRecord | null> {
  const { data } = await apiClient.get(`/contracts/${id}/signature`)
  return data.data as SignatureRecord | null
}
