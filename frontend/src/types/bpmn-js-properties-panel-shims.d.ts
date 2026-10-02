// bpmn-js-properties-panel and @bpmn-io/properties-panel ship no TypeScript
// declarations (no "types"/"typings" field, no @types package). These ambient
// shims type only the exports this codebase actually uses, kept intentionally
// loose (unknown/any at the boundary) since the real shapes are preact
// components and a small didi module descriptor.

declare module 'bpmn-js-properties-panel' {
  export const BpmnPropertiesPanelModule: unknown
  export const BpmnPropertiesProviderModule: unknown

  export function useService<T = unknown>(name: string): T
}

declare module '@bpmn-io/properties-panel' {
  export type PropertiesPanelEntryProps = {
    element: unknown
    id: string
  }

  export const TextFieldEntry: (props: {
    element: unknown
    id: string
    label: string
    description?: string
    getValue: () => string
    setValue: (value: string | undefined) => void
    debounce?: <A extends unknown[]>(fn: (...args: A) => void) => (...args: A) => void
    validate?: (value: string) => string | undefined
  }) => unknown

  export const TextAreaEntry: (props: {
    element: unknown
    id: string
    label: string
    description?: string
    rows?: number
    getValue: () => string
    setValue: (value: string | undefined) => void
    debounce?: <A extends unknown[]>(fn: (...args: A) => void) => (...args: A) => void
    validate?: (value: string) => string | undefined
  }) => unknown

  export const SelectEntry: (props: {
    element: unknown
    id: string
    label: string
    description?: string
    getValue: () => string
    setValue: (value: string) => void
    getOptions: () => Array<{ value: string; label: string }>
  }) => unknown

  // Composant de groupe (liste d'éléments repliables) : utilisé comme `component` d'un groupe, jamais appelé.
  export const ListGroup: unknown

  export const CheckboxEntry: (props: {
    element: unknown
    id: string
    label: string
    description?: string
    getValue: () => boolean
    setValue: (value: boolean) => void
  }) => unknown

  export function isTextFieldEntryEdited(node: unknown): boolean
  export function isTextAreaEntryEdited(node: unknown): boolean
  export function isCheckboxEntryEdited(node: unknown): boolean
  export function isSelectEntryEdited(node: unknown): boolean
}

declare module '@bpmn-io/properties-panel/preact/hooks' {
  export function useState<T>(initial: T): [T, (value: T) => void]
  export function useEffect(effect: () => void | (() => void), deps?: unknown[]): void
}
