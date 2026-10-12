import { describe, expect, it, vi } from 'vitest'
import type { RouteLocationNormalized } from 'vue-router'
import { createRoleGuard } from './roleGuard'

function route(roles?: string[]): RouteLocationNormalized {
  return { meta: roles ? { roles } : {} } as unknown as RouteLocationNormalized
}

async function run(guard: ReturnType<typeof createRoleGuard>, to: RouteLocationNormalized) {
  return (guard as (to: RouteLocationNormalized) => Promise<unknown>)(to)
}

describe('role guard', () => {
  it('public routes pass without waiting for /me', async () => {
    const ensure = vi.fn(() => new Promise<void>(() => {}))
    const guard = createRoleGuard(ensure, () => null)
    await expect(run(guard, route())).resolves.toBe(true)
    expect(ensure).not.toHaveBeenCalled()
  })

  it('waits for the user before deciding — a direct load must not let a viewer in', async () => {
    let roles: string[] | null = null
    const ensure = vi.fn(async () => {
      roles = ['DOSSIER_VIEWER']
    })
    const guard = createRoleGuard(ensure, () => roles)
    await expect(run(guard, route(['TENANT_ADMIN']))).resolves.toEqual({ name: 'supervision' })
    expect(ensure).toHaveBeenCalledOnce()
  })

  it('lets an authorised role in, and redirects when no user could be loaded', async () => {
    const admin = createRoleGuard(async () => {}, () => ['WORKFLOW_ADMIN'])
    await expect(run(admin, route(['TENANT_ADMIN', 'WORKFLOW_ADMIN']))).resolves.toBe(true)
    const anonymous = createRoleGuard(async () => {}, () => null)
    await expect(run(anonymous, route(['TENANT_ADMIN']))).resolves.toEqual({ name: 'supervision' })
  })
})
