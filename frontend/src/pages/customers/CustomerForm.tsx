import { useState } from 'react'
import type { CustomerPayload } from '../../services/customerService'

interface Props {
  initial?: CustomerPayload
  onSubmit: (payload: CustomerPayload) => Promise<unknown>
  onCancel: () => void
  submitting?: boolean
}

const emptyPayload: CustomerPayload = {
  name: '',
  company: '',
  contactPerson: '',
  phone: '',
  email: '',
  address: '',
  remark: '',
}

export default function CustomerForm({ initial, onSubmit, onCancel, submitting }: Props) {
  const [form, setForm] = useState<CustomerPayload>({ ...emptyPayload, ...initial })

  const set = (field: keyof CustomerPayload) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [field]: e.target.value }))

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    // 剔除空字符串可选字段（后端 @Pattern/@Email 对空串校验失败，null/undefined 才视为可选）
    const payload: CustomerPayload = { name: form.name, company: form.company }
    for (const [key, value] of Object.entries(form)) {
      if (key !== 'name' && key !== 'company' && value !== undefined && value !== null && value !== '') {
        payload[key as keyof CustomerPayload] = value as never
      }
    }
    payload.version = initial?.version
    await onSubmit(payload)
  }

  const inputClass =
    'w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none'

  return (
    <form onSubmit={handleSubmit} className="space-y-4" aria-label="客户表单">
      <div className="grid grid-cols-2 gap-4">
        <div>
          <label htmlFor="customer-name" className="mb-1 block text-sm font-medium text-gray-700">
            客户名称 <span className="text-red-500">*</span>
          </label>
          <input
            id="customer-name"
            value={form.name}
            onChange={set('name')}
            required
            className={inputClass}
          />
        </div>
        <div>
          <label htmlFor="customer-company" className="mb-1 block text-sm font-medium text-gray-700">
            公司 <span className="text-red-500">*</span>
          </label>
          <input
            id="customer-company"
            value={form.company}
            onChange={set('company')}
            required
            className={inputClass}
          />
        </div>
        <div>
          <label htmlFor="customer-contact" className="mb-1 block text-sm font-medium text-gray-700">
            联系人
          </label>
          <input
            id="customer-contact"
            value={form.contactPerson ?? ''}
            onChange={set('contactPerson')}
            className={inputClass}
          />
        </div>
        <div>
          <label htmlFor="customer-phone" className="mb-1 block text-sm font-medium text-gray-700">
            电话
          </label>
          <input
            id="customer-phone"
            value={form.phone ?? ''}
            onChange={set('phone')}
            className={inputClass}
          />
        </div>
        <div>
          <label htmlFor="customer-email" className="mb-1 block text-sm font-medium text-gray-700">
            邮箱
          </label>
          <input
            id="customer-email"
            value={form.email ?? ''}
            onChange={set('email')}
            type="email"
            className={inputClass}
          />
        </div>
        <div>
          <label htmlFor="customer-address" className="mb-1 block text-sm font-medium text-gray-700">
            地址
          </label>
          <input
            id="customer-address"
            value={form.address ?? ''}
            onChange={set('address')}
            className={inputClass}
          />
        </div>
      </div>
      <div>
        <label htmlFor="customer-remark" className="mb-1 block text-sm font-medium text-gray-700">
          备注
        </label>
        <input
          id="customer-remark"
          value={form.remark ?? ''}
          onChange={set('remark')}
          className={inputClass}
        />
      </div>
      <div className="flex justify-end gap-2">
        <button
          type="button"
          onClick={onCancel}
          className="rounded border border-gray-300 px-4 py-2 hover:bg-gray-100"
        >
          取消
        </button>
        <button
          type="submit"
          disabled={submitting}
          className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {submitting ? '保存中…' : '保存'}
        </button>
      </div>
    </form>
  )
}
