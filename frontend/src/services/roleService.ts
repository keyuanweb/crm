import { apiClient, type PageResult } from './apiClient'
import type { MenuTreeNode, PermissionDefGroup, Role, RoleOption, RolePayload } from '../types/role'

export async function fetchRoles(params: { page?: number; pageSize?: number }): Promise<PageResult<Role>> {
  const { data } = await apiClient.get('/roles', { params })
  return data.data as PageResult<Role>
}

export async function createRole(payload: RolePayload): Promise<Role> {
  const { data } = await apiClient.post('/roles', payload)
  return data.data as Role
}

export async function updateRole(id: number, payload: RolePayload): Promise<Role> {
  const { data } = await apiClient.put(`/roles/${id}`, payload)
  return data.data as Role
}

export async function deleteRole(id: number): Promise<void> {
  await apiClient.delete(`/roles/${id}`)
}

export async function fetchRoleOptions(): Promise<RoleOption[]> {
  const { data } = await apiClient.get('/roles/options')
  return data.data as RoleOption[]
}

export async function fetchMenuTree(): Promise<MenuTreeNode[]> {
  const { data } = await apiClient.get('/roles/menu-tree')
  return data.data as MenuTreeNode[]
}

export async function fetchPermissionDefs(): Promise<PermissionDefGroup[]> {
  const { data } = await apiClient.get('/roles/permission-defs')
  return data.data as PermissionDefGroup[]
}
