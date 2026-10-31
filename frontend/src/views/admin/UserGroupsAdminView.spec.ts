import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import UserGroupsAdminView from './UserGroupsAdminView.vue'
import * as api from '@/api/userGroups'
import { routes } from '@/app/routes'

vi.mock('@/api/userGroups')

const compta = {
  code: 'COMPTABILITE',
  label: 'Comptabilité',
  idpGroup: '/services/compta',
  members: [{ userId: 'alice', displayName: 'Alice' }],
}

async function mountView() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/admin/user-groups')
  await router.isReady()
  const wrapper = mount(UserGroupsAdminView, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

describe('UserGroupsAdminView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listUserGroups).mockResolvedValue([compta])
    vi.mocked(api.listUserDirectory).mockResolvedValue([
      { userId: 'alice', displayName: 'Alice', email: 'alice@example.org' },
      { userId: 'bob', displayName: 'Bob', email: null },
    ])
  })

  it('lists the groups with their members and the mapped identity-provider group', async () => {
    const wrapper = await mountView()
    expect(wrapper.text()).toContain('COMPTABILITE')
    expect(wrapper.text()).toContain('Alice (alice@example.org)')
    expect(wrapper.text()).toContain('« /services/compta »')
    const options = wrapper.get('[data-testid="group-member-select-COMPTABILITE"]').findAll('option')
    expect(options.map((option) => option.text())).toEqual(['Choisir un utilisateur…', 'Bob'])
  })

  it('creates a group with a trimmed code and no identity-provider group when left blank', async () => {
    vi.mocked(api.createUserGroup).mockResolvedValue({ code: 'RH', label: 'RH', idpGroup: null, members: [] })
    const wrapper = await mountView()
    await wrapper.get('[data-testid="group-code"]').setValue(' RH ')
    await wrapper.get('[data-testid="group-label"]').setValue('Ressources humaines')
    await wrapper.get('[data-testid="group-create-form"]').trigger('submit.prevent')
    await flushPromises()
    expect(api.createUserGroup).toHaveBeenCalledWith({ code: 'RH', label: 'Ressources humaines', idpGroup: null })
    expect(api.listUserGroups).toHaveBeenCalledTimes(2)
  })

  it('adds and removes a member, and saves the label', async () => {
    vi.mocked(api.addUserGroupMember).mockResolvedValue({
      ...compta,
      members: [...compta.members, { userId: 'bob', displayName: 'Bob' }],
    })
    vi.mocked(api.removeUserGroupMember).mockResolvedValue({ ...compta, members: [] })
    vi.mocked(api.updateUserGroup).mockResolvedValue({ ...compta, label: 'Service comptable', members: [] })
    const wrapper = await mountView()
    await wrapper.get('[data-testid="group-member-select-COMPTABILITE"]').setValue('bob')
    await wrapper.get('[data-testid="group-member-add-COMPTABILITE"]').trigger('click')
    await flushPromises()
    expect(api.addUserGroupMember).toHaveBeenCalledWith('COMPTABILITE', 'bob')
    expect(wrapper.text()).toContain('Bob')
    await wrapper.get('[data-testid="group-member-remove-COMPTABILITE-alice"]').trigger('click')
    await flushPromises()
    expect(api.removeUserGroupMember).toHaveBeenCalledWith('COMPTABILITE', 'alice')
    await wrapper.get('[data-testid="group-edit-label-COMPTABILITE"]').setValue('Service comptable')
    await wrapper.get('[data-testid="group-save-COMPTABILITE"]').trigger('click')
    await flushPromises()
    expect(api.updateUserGroup).toHaveBeenCalledWith('COMPTABILITE', { label: 'Service comptable', idpGroup: '/services/compta' })
  })

  it('shows the reason of a refusal', async () => {
    vi.mocked(api.addUserGroupMember).mockRejectedValue({
      response: { status: 400, data: { detail: 'Unknown user (never logged in to this instance): bob', status: 400 } },
    })
    const wrapper = await mountView()
    await wrapper.get('[data-testid="group-member-select-COMPTABILITE"]').setValue('bob')
    await wrapper.get('[data-testid="group-member-add-COMPTABILITE"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="group-error"]').text()).toContain('never logged in')
  })

  it('deletes a group', async () => {
    vi.mocked(api.deleteUserGroup).mockResolvedValue()
    const wrapper = await mountView()
    await wrapper.get('[data-testid="group-remove-COMPTABILITE"]').trigger('click')
    await flushPromises()
    expect(api.deleteUserGroup).toHaveBeenCalledWith('COMPTABILITE')
  })
})
