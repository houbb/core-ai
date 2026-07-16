export interface RuntimeAction {
  label: string
  method?: string
  path: string
  payload?: unknown
  tone?: 'secondary' | 'emphasis' | 'primary' | 'danger'
}
