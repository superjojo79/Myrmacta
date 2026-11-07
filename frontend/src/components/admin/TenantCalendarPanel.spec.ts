import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import TenantCalendarPanel from './TenantCalendarPanel.vue'
import * as calendarApi from '@/api/tenantCalendar'

vi.mock('@/api/tenantCalendar')

const stored = {
  tenantId: 'tenant_dev',
  zoneId: 'Europe/Brussels',
  weekend: 'SATURDAY_SUNDAY' as const,
  holidays: ['2026-11-11'],
  updatedAt: '2026-11-06T00:00:00Z',
  updatedBy: 'admin',
}

describe('TenantCalendarPanel (TODO §120)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(calendarApi.getTenantCalendar).mockResolvedValue(stored)
  })

  it('shows the tenant calendar and saves the edited holidays', async () => {
    vi.mocked(calendarApi.replaceTenantCalendar).mockResolvedValue({ ...stored, holidays: ['2026-11-11', '2026-12-25'] })
    const wrapper = mount(TenantCalendarPanel)
    await flushPromises()
    expect((wrapper.get('[data-testid="calendar-zone"]').element as HTMLInputElement).value).toBe('Europe/Brussels')
    await wrapper.get('[data-testid="calendar-holidays"]').setValue('2026-11-11\n2026-12-25')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(calendarApi.replaceTenantCalendar).toHaveBeenCalledWith({
      zoneId: 'Europe/Brussels',
      weekend: 'SATURDAY_SUNDAY',
      holidays: ['2026-11-11', '2026-12-25'],
    })
    expect(wrapper.find('[data-testid="calendar-saved"]').exists()).toBe(true)
  })
})
