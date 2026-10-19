import axios from 'axios'

/** TODO §75 (LC2) — validation à blanc d'une expression métier (JSONata borné) : 200 même si elle est invalide. */
export interface ExpressionValidation {
  valid: boolean
  message?: string | null
  position?: number | null
}

export async function validateExpression(expression: string): Promise<ExpressionValidation> {
  const { data } = await axios.post<ExpressionValidation>('/api/expressions/validate', { expression })
  return data
}
