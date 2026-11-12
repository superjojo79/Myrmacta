/** OMG DMN 1.1 model namespace (legacy tables). */
export const DMN_11_NAMESPACE = 'http://www.omg.org/spec/DMN/20151101'

/** OMG DMN 1.3 model namespace — target for {@code dmn-js} and MCP generation (TODO §130). */
export const DMN_13_NAMESPACE = 'https://www.omg.org/spec/DMN/20191111/MODEL/'

const DMN_11_XMLNS = /xmlns\s*=\s*(["'])http:\/\/www\.omg\.org\/spec\/DMN\/20151101\1/g

/**
 * Rewrites a DMN 1.1 model {@code xmlns} to DMN 1.3 so {@code dmn-js} can open the document.
 * Strips XML comments before the root (Flowable rejects DMN 1.3 with a leading comment).
 * Linear scan — unclosed {@code <!--} does not backtrack. Does not alter cell text.
 */
export function toDmn13(xml: string): string {
  if (!xml) {
    return xml
  }
  const has11 = xml.includes(DMN_11_NAMESPACE)
  const has13 = xml.includes(DMN_13_NAMESPACE)
  if (!has11 && !has13) {
    return xml
  }
  let out = stripCommentsBeforeRoot(xml)
  if (has11) {
    out = out.replace(DMN_11_XMLNS, `xmlns=$1${DMN_13_NAMESPACE}$1`)
  }
  return out
}

/** Removes {@code <!-- … -->} only before the first element (after an optional XML declaration). */
export function stripCommentsBeforeRoot(xml: string): string {
  let out = ''
  let i = 0
  const n = xml.length
  while (i < n) {
    while (i < n && /\s/.test(xml[i]!)) {
      out += xml[i]
      i++
    }
    if (xml.startsWith('<!--', i)) {
      const end = xml.indexOf('-->', i + 4)
      if (end < 0) {
        return out + xml.slice(i)
      }
      i = end + 3
      continue
    }
    if (xml.startsWith('<?xml', i)) {
      const end = xml.indexOf('?>', i + 5)
      if (end < 0) {
        return out + xml.slice(i)
      }
      out += xml.slice(i, end + 2)
      i = end + 2
      continue
    }
    out += xml.slice(i)
    break
  }
  return out
}
