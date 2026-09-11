// dmn-js ships no TypeScript declarations (no "types"/"typings" field, no
// @types package) -- same situation as bpmn-js-properties-panel, see
// bpmn-js-properties-panel-shims.d.ts. Typed only loosely (unknown/any at the
// boundary): DmnEditorCanvas.vue re-declares the concrete shape it actually
// uses (ModelerInstance) on top of this default-export shim.

declare module 'dmn-js/lib/Modeler' {
  const Modeler: unknown
  export default Modeler
}
