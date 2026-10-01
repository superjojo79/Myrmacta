<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { Editor, EditorContent } from '@tiptap/vue-3'
import { templateExtensions } from '@/templateEditor/extensions'
import { analyzeTemplateHtml, isSafeLiteral } from '@/templateEditor/htmlSubset'
import { buildVariableCatalog, isIdentifier, isVariablePath } from '@/templateEditor/variableCatalog'

/**
 * TODO §84 (LC4b) — éditeur visuel d'un template de document. N'offre que ce que Flying Saucer (CSS 2.1) rend et ce que
 * l'allowlist des expressions accepte ; un template hors sous-ensemble reste en mode source (rien n'est abîmé en silence).
 * `modelValue` est le HTML Thymeleaf : c'est lui le contrat, pas un format propre à l'éditeur.
 */
const props = defineProps<{
  modelValue: string
  /** Variables d'exemple (celles de l'aperçu) : alimentent le menu des variables. */
  sampleVariables?: Record<string, unknown>
}>()
const emit = defineEmits<{ 'update:modelValue': [html: string] }>()

/** Ce que l'éditeur écrirait pour ce HTML : sert à vérifier qu'il le restitue sans rien perdre (revue adversariale LC4b). */
function renderThroughEditor(html: string): string {
  const probe = new Editor({ extensions: templateExtensions(), content: html })
  try {
    return probe.getHTML()
  } finally {
    probe.destroy()
  }
}
const analyze = (html: string) => analyzeTemplateHtml(html, renderThroughEditor)

const initial = analyze(props.modelValue)
const mode = ref<'visual' | 'source'>(initial.compatible ? 'visual' : 'source')
const sourceNotice = ref<string[]>(initial.compatible ? [] : initial.reasons)
const editor = shallowRef<Editor | null>(null)
let lastEmitted = props.modelValue

function createEditor(html: string) {
  editor.value?.destroy()
  const instance = new Editor({
    extensions: templateExtensions(),
    content: html,
    onUpdate: ({ editor: e }) => {
      lastEmitted = e.getHTML()
      emit('update:modelValue', lastEmitted)
    },
  })
  editor.value = instance
}

if (mode.value === 'visual') createEditor(props.modelValue)

onBeforeUnmount(() => editor.value?.destroy())

// Le parent charge un autre template : on repart de son contenu (et on re-décide du mode).
watch(
  () => props.modelValue,
  (html) => {
    if (html === lastEmitted) return
    lastEmitted = html
    const analysis = analyze(html)
    if (analysis.compatible) {
      sourceNotice.value = []
      mode.value = 'visual'
      createEditor(html)
    } else {
      sourceNotice.value = analysis.reasons
      mode.value = 'source'
      editor.value?.destroy()
      editor.value = null
    }
  },
)

function onSource(event: Event) {
  const html = (event.target as HTMLTextAreaElement).value
  lastEmitted = html
  emit('update:modelValue', html)
}

function showVisual() {
  const analysis = analyze(props.modelValue)
  if (!analysis.compatible) {
    sourceNotice.value = analysis.reasons
    return
  }
  sourceNotice.value = []
  mode.value = 'visual'
  createEditor(props.modelValue)
}

function showSource() {
  mode.value = 'source'
  sourceNotice.value = []
  editor.value?.destroy()
  editor.value = null
}

const catalog = computed(() => buildVariableCatalog(props.sampleVariables ?? {}))

// ---- insertions ----
const formError = ref<string | null>(null)
const variablePath = ref('')
const conditionMode = ref<'if' | 'unless' | 'equals'>('if')
const conditionPath = ref('')
const conditionValue = ref('')
const repeatPath = ref('')
const repeatItem = ref('ligne')
const rowPath = ref('')

/** Variables proposées : celles de l'exemple ; dans un bloc répété, les champs de l'élément sous `item.champ`. */
const variableSuggestions = computed(() => {
  const suggestions = [...catalog.value.scalars]
  const e = editor.value
  if (e) {
    const { $from } = e.state.selection
    for (let depth = $from.depth; depth > 0; depth--) {
      const node = $from.node(depth)
      if (node.type.name === 'repeatBlock' || (node.type.name === 'tableRow' && node.attrs.each)) {
        const each = node.type.name === 'repeatBlock' ? { item: String(node.attrs.item), path: String(node.attrs.path) } : (node.attrs.each as { item: string; path: string })
        const list = catalog.value.lists.find((l) => l.path === each.path)
        for (const field of list?.itemFields ?? []) suggestions.push(`${each.item}.${field}`)
      }
    }
  }
  return suggestions
})

function insertVariable() {
  formError.value = null
  const path = variablePath.value.trim()
  if (!isVariablePath(path)) {
    formError.value = 'Chemin de variable invalide (lettres, chiffres, _ et points : client.nom)'
    return
  }
  editor.value?.chain().focus().insertContent({ type: 'variable', attrs: { path } }).run()
  variablePath.value = ''
}

function wrapConditional() {
  formError.value = null
  const path = conditionPath.value.trim()
  if (!isVariablePath(path)) {
    formError.value = 'Chemin de variable invalide pour la condition'
    return
  }
  if (conditionMode.value === 'equals' && !isSafeLiteral(conditionValue.value)) {
    formError.value = "La valeur ne doit contenir ni apostrophe, ni antislash, ni accolade, ni $ (200 caractères au plus)"
    return
  }
  const attrs = { mode: conditionMode.value, path, value: conditionMode.value === 'equals' ? conditionValue.value : '' }
  editor.value?.chain().focus().wrapIn('conditionalBlock', attrs).run()
}

function wrapRepeat() {
  formError.value = null
  const path = repeatPath.value.trim()
  const item = repeatItem.value.trim()
  if (!isVariablePath(path) || !isIdentifier(item)) {
    formError.value = 'Liste et nom d’élément invalides (ex. lignes / ligne)'
    return
  }
  editor.value?.chain().focus().wrapIn('repeatBlock', { item, path }).run()
}

function repeatRow() {
  formError.value = null
  const path = rowPath.value.trim()
  const item = repeatItem.value.trim()
  if (!isVariablePath(path) || !isIdentifier(item)) {
    formError.value = 'Liste et nom d’élément invalides (ex. lignes / ligne)'
    return
  }
  editor.value?.chain().focus().updateAttributes('tableRow', { each: { item, path } }).run()
}

function stopRepeatRow() {
  editor.value?.chain().focus().updateAttributes('tableRow', { each: null }).run()
}

const chain = () => editor.value?.chain().focus()
</script>

<template>
  <div class="tpl-editor" data-testid="template-editor">
    <div class="tpl-editor__modes" role="tablist">
      <button type="button" role="tab" :aria-selected="mode === 'visual'" data-testid="mode-visual" @click="showVisual">Éditeur visuel</button>
      <button type="button" role="tab" :aria-selected="mode === 'source'" data-testid="mode-source" @click="showSource">Source HTML</button>
    </div>
    <div v-if="sourceNotice.length" class="tpl-editor__notice" role="status" data-testid="source-notice">
      Ce template utilise des éléments que l’éditeur visuel ne sait pas écrire sans les altérer : il reste en mode source.
      <ul>
        <li v-for="reason in sourceNotice" :key="reason">{{ reason }}</li>
      </ul>
    </div>

    <template v-if="mode === 'visual' && editor">
      <div class="tpl-editor__toolbar" role="toolbar" aria-label="Mise en forme">
        <button type="button" title="Gras" data-testid="tb-bold" @click="chain()?.toggleBold().run()"><strong>G</strong></button>
        <button type="button" title="Italique" data-testid="tb-italic" @click="chain()?.toggleItalic().run()"><em>I</em></button>
        <button type="button" title="Souligné" data-testid="tb-underline" @click="chain()?.toggleUnderline().run()"><u>S</u></button>
        <button type="button" title="Paragraphe" @click="chain()?.setParagraph().run()">¶</button>
        <button type="button" title="Titre 1" data-testid="tb-h1" @click="chain()?.toggleHeading({ level: 1 }).run()">H1</button>
        <button type="button" title="Titre 2" @click="chain()?.toggleHeading({ level: 2 }).run()">H2</button>
        <button type="button" title="Titre 3" @click="chain()?.toggleHeading({ level: 3 }).run()">H3</button>
        <button type="button" title="Liste à puces" data-testid="tb-bullets" @click="chain()?.toggleBulletList().run()">• Liste</button>
        <button type="button" title="Liste numérotée" @click="chain()?.toggleOrderedList().run()">1. Liste</button>
        <button type="button" title="Aligner à gauche" @click="chain()?.setTextAlign('left').run()">⇤</button>
        <button type="button" title="Centrer" data-testid="tb-center" @click="chain()?.setTextAlign('center').run()">↔</button>
        <button type="button" title="Aligner à droite" @click="chain()?.setTextAlign('right').run()">⇥</button>
        <button type="button" title="Ligne horizontale" @click="chain()?.setHorizontalRule().run()">—</button>
        <button type="button" title="Saut de page" data-testid="tb-pagebreak" @click="chain()?.insertContent({ type: 'pageBreak' }).run()">⏎ page</button>
        <button type="button" title="Tableau 3×2" data-testid="tb-table" @click="chain()?.insertTable({ rows: 2, cols: 3, withHeaderRow: true }).run()">Tableau</button>
        <button type="button" title="Ajouter une ligne" @click="chain()?.addRowAfter().run()">+ ligne</button>
        <button type="button" title="Ajouter une colonne" @click="chain()?.addColumnAfter().run()">+ col.</button>
        <button type="button" title="Supprimer le tableau" @click="chain()?.deleteTable().run()">✕ tableau</button>
      </div>

      <datalist id="tpl-variables">
        <option v-for="v in variableSuggestions" :key="v" :value="v" />
      </datalist>
      <datalist id="tpl-lists">
        <option v-for="l in catalog.lists" :key="l.path" :value="l.path" />
      </datalist>

      <div class="tpl-editor__inserts">
        <fieldset>
          <legend>Variable</legend>
          <input v-model="variablePath" list="tpl-variables" placeholder="client.nom" aria-label="Chemin de la variable" data-testid="variable-path" />
          <button type="button" data-testid="insert-variable" @click="insertVariable">Insérer</button>
        </fieldset>
        <fieldset>
          <legend>Bloc conditionnel</legend>
          <select v-model="conditionMode" aria-label="Type de condition" data-testid="condition-mode">
            <option value="if">si renseigné</option>
            <option value="unless">sauf si renseigné</option>
            <option value="equals">si égal à</option>
          </select>
          <input v-model="conditionPath" list="tpl-variables" placeholder="client.pro" aria-label="Variable de la condition" data-testid="condition-path" />
          <input v-if="conditionMode === 'equals'" v-model="conditionValue" placeholder="PRO" aria-label="Valeur" data-testid="condition-value" />
          <button type="button" data-testid="wrap-conditional" @click="wrapConditional">Entourer la sélection</button>
        </fieldset>
        <fieldset>
          <legend>Répétition</legend>
          <input v-model="repeatPath" list="tpl-lists" placeholder="lignes" aria-label="Liste à répéter" data-testid="repeat-path" />
          <input v-model="repeatItem" placeholder="ligne" aria-label="Nom d’un élément" data-testid="repeat-item" />
          <button type="button" data-testid="wrap-repeat" @click="wrapRepeat">Répéter la sélection</button>
          <input v-model="rowPath" list="tpl-lists" placeholder="lignes (tableau)" aria-label="Liste du tableau" data-testid="row-path" />
          <button type="button" data-testid="repeat-row" @click="repeatRow">Répéter la ligne du tableau</button>
          <button type="button" @click="stopRepeatRow">Ne plus répéter la ligne</button>
        </fieldset>
      </div>
      <p v-if="formError" class="tpl-editor__error" role="alert" data-testid="editor-error">{{ formError }}</p>

      <EditorContent :editor="editor" class="tpl-editor__content" data-testid="editor-content" />
    </template>

    <textarea
      v-else-if="mode === 'source'"
      class="tpl-editor__source"
      rows="14"
      spellcheck="false"
      :value="modelValue"
      data-testid="source-textarea"
      @input="onSource"
    />
  </div>
</template>

<style scoped>
.tpl-editor {
  display: grid;
  gap: 0.5rem;
}
.tpl-editor__modes button[aria-selected='true'] {
  font-weight: 700;
  text-decoration: underline;
}
.tpl-editor__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}
.tpl-editor__inserts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}
.tpl-editor__inserts fieldset {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
  align-items: center;
}
.tpl-editor__notice {
  background: #fff3bf;
  padding: 0.5rem 0.75rem;
  border-radius: 0.3rem;
}
.tpl-editor__error {
  color: #c92a2a;
  margin: 0;
}
.tpl-editor__content :deep(.ProseMirror) {
  min-height: 12rem;
  border: 1px solid #ccc;
  padding: 0.5rem 0.75rem;
  background: #fff;
}
.tpl-editor__content :deep(.ProseMirror table) {
  border-collapse: collapse;
  width: 100%;
}
.tpl-editor__content :deep(.ProseMirror td),
.tpl-editor__content :deep(.ProseMirror th) {
  border: 1px solid #bbb;
  padding: 0.25rem 0.5rem;
}
.tpl-editor__content :deep(.ProseMirror span[th\:text]) {
  background: #e7f5ff;
  border-radius: 0.2rem;
  padding: 0 0.2rem;
}
.tpl-editor__content :deep(.ProseMirror div[th\:if]),
.tpl-editor__content :deep(.ProseMirror div[th\:unless]) {
  border-left: 3px solid #fab005;
  padding-left: 0.5rem;
}
.tpl-editor__content :deep(.ProseMirror div[th\:each]) {
  border-left: 3px solid #51cf66;
  padding-left: 0.5rem;
}
.tpl-editor__source {
  font-family: ui-monospace, monospace;
  width: 100%;
}
</style>
