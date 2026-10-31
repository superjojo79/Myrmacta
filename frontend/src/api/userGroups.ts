import axios from 'axios'

/** TODO §114 (issue #190) — groupes d'utilisateurs (services) et annuaire local, réservés à l'administrateur du tenant. */
export interface UserGroupMember {
  userId: string
  displayName: string
}

export interface UserGroup {
  code: string
  label: string
  idpGroup: string | null
  members: UserGroupMember[]
}

export interface DirectoryUser {
  userId: string
  displayName: string
  email: string | null
}

const base = '/api/user-groups'

function path(code: string): string {
  return `${base}/${encodeURIComponent(code)}`
}

export async function listUserGroups(): Promise<UserGroup[]> {
  const { data } = await axios.get<UserGroup[]>(base)
  return data
}

export async function createUserGroup(payload: { code: string; label: string; idpGroup?: string | null }): Promise<UserGroup> {
  const { data } = await axios.post<UserGroup>(base, payload)
  return data
}

export async function updateUserGroup(code: string, payload: { label: string; idpGroup: string | null }): Promise<UserGroup> {
  const { data } = await axios.put<UserGroup>(path(code), payload)
  return data
}

export async function deleteUserGroup(code: string): Promise<void> {
  await axios.delete(path(code))
}

export async function addUserGroupMember(code: string, userId: string): Promise<UserGroup> {
  const { data } = await axios.put<UserGroup>(`${path(code)}/members/${encodeURIComponent(userId)}`)
  return data
}

export async function removeUserGroupMember(code: string, userId: string): Promise<UserGroup> {
  const { data } = await axios.delete<UserGroup>(`${path(code)}/members/${encodeURIComponent(userId)}`)
  return data
}

export async function listUserDirectory(): Promise<DirectoryUser[]> {
  const { data } = await axios.get<DirectoryUser[]>(`${base}/directory`)
  return data
}
