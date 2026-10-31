import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import {
  addUserGroupMember,
  createUserGroup,
  deleteUserGroup,
  listUserDirectory,
  listUserGroups,
  removeUserGroupMember,
  updateUserGroup,
} from './userGroups'

vi.mock('axios')

describe('userGroups api', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('lists the groups and the directory', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: [] })
    await listUserGroups()
    await listUserDirectory()
    expect(axios.get).toHaveBeenCalledWith('/api/user-groups')
    expect(axios.get).toHaveBeenCalledWith('/api/user-groups/directory')
  })

  it('creates updates and deletes a group', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: { code: 'RH' } })
    vi.mocked(axios.put).mockResolvedValue({ data: { code: 'RH' } })
    vi.mocked(axios.delete).mockResolvedValue({})
    await createUserGroup({ code: 'RH', label: 'Ressources humaines', idpGroup: null })
    await updateUserGroup('RH', { label: 'RH', idpGroup: '/services/rh' })
    await deleteUserGroup('RH')
    expect(axios.post).toHaveBeenCalledWith('/api/user-groups', { code: 'RH', label: 'Ressources humaines', idpGroup: null })
    expect(axios.put).toHaveBeenCalledWith('/api/user-groups/RH', { label: 'RH', idpGroup: '/services/rh' })
    expect(axios.delete).toHaveBeenCalledWith('/api/user-groups/RH')
  })

  it('adds and removes a member, encoding the subject', async () => {
    vi.mocked(axios.put).mockResolvedValue({ data: { code: 'RH' } })
    vi.mocked(axios.delete).mockResolvedValue({ data: { code: 'RH' } })
    await addUserGroupMember('RH', 'a/b|c')
    await removeUserGroupMember('RH', 'a/b|c')
    expect(axios.put).toHaveBeenCalledWith('/api/user-groups/RH/members/a%2Fb%7Cc')
    expect(axios.delete).toHaveBeenCalledWith('/api/user-groups/RH/members/a%2Fb%7Cc')
  })
})
