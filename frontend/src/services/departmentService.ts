import { apiClient } from './apiClient'
import type { Department, DepartmentPayload } from '../types/department'

export type { DepartmentPayload }

export async function fetchDepartmentTree(): Promise<Department[]> {
  const { data } = await apiClient.get('/departments/tree')
  return (data.data as { departments: Department[] }).departments
}

export async function createDepartment(payload: DepartmentPayload): Promise<Department> {
  const { data } = await apiClient.post('/departments', payload)
  return data.data as Department
}

export async function updateDepartment(id: number, payload: DepartmentPayload): Promise<Department> {
  const { data } = await apiClient.put(`/departments/${id}`, payload)
  return data.data as Department
}

export async function deleteDepartment(id: number): Promise<void> {
  await apiClient.delete(`/departments/${id}`)
}

/** 设置用户部门与数据权限（仅管理员）。 */
export async function setUserDataPermission(
  userId: number,
  payload: { departmentId?: number; dataScope?: string },
): Promise<unknown> {
  const { data } = await apiClient.put(`/users/${userId}/data-permission`, payload)
  return data.data
}
