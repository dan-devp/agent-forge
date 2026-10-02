import type { ReactNode } from 'react'
import type { EvalConfig } from '../api/client'
import { listValue } from './evalGuide'
import { Ref } from './Ref'

const str = (v: unknown) => (v === undefined || v === null ? '' : String(v))
const bool = (v: unknown, fallback: boolean) => (v === undefined || v === null || v === '' ? fallback : v === true || v === 'true')

function Quote({ text }: { text: unknown }) {
  const t = str(text)
  return t ? <q className="eval-quote">{t}</q> : <span className="eval-missing">…</span>
}

function Code({ text }: { text: unknown }) {
  const t = str(text)
  return t ? <code className="eval-code">{t}</code> : <span className="eval-missing">…</span>
}

function ToolName({ name }: { name: unknown }) {
  const t = str(name)
  return t ? <Ref kind="tool" id={t} /> : <span className="eval-missing">…</span>
}

function JudgeModel({ id }: { id: unknown }) {
  const t = str(id)
  return t ? <Ref kind="model" id={t} /> : <span className="eval-missing">Judge-Model fehlt</span>
}

/**
 * One eval as a sentence: "Antwort enthält „Hauptstadt“" instead of contains text=Hauptstadt.
 * A missing value shows as "…", so an incomplete eval is visible in the sentence itself.
 */
export function EvalSentence({ config, detail = true }: { config: EvalConfig; detail?: boolean }) {
  const p = config.params
  let body: ReactNode
  switch (config.type) {
    case 'contains':
      body = (
        <>
          Antwort enthält <Quote text={p.text} />
          {bool(p['ignore-case'], false) && <span className="muted"> (Groß/klein egal)</span>}
        </>
      )
      break
    case 'not-contains':
      body = (
        <>
          Antwort enthält <strong>nicht</strong> <Quote text={p.text} />
          {bool(p['ignore-case'], false) && <span className="muted"> (Groß/klein egal)</span>}
        </>
      )
      break
    case 'equals':
      body = (
        <>
          Antwort ist genau <Quote text={p.text} />
          {!bool(p['ignore-case'], true) && <span className="muted"> (Groß/klein zählt)</span>}
        </>
      )
      break
    case 'regex':
      body = (
        <>
          Antwort passt auf <Code text={p.pattern} />
        </>
      )
      break
    case 'not-regex':
      body = (
        <>
          Antwort passt <strong>nicht</strong> auf <Code text={p.pattern} />
        </>
      )
      break
    case 'length': {
      const unit = str(p.unit) === 'chars' ? 'Zeichen' : 'Wörter'
      const min = str(p.min)
      const max = str(p.max)
      body =
        min && max ? (
          <>
            Antwort hat zwischen {min} und {max} {unit}
          </>
        ) : max ? (
          <>
            Antwort hat höchstens {max} {unit}
          </>
        ) : min ? (
          <>
            Antwort hat mindestens {min} {unit}
          </>
        ) : (
          <>
            Antwort-Länge <span className="eval-missing">ohne Grenze</span>
          </>
        )
      break
    }
    case 'tool-called':
      body =
        str(p.tool) === '*' ? (
          <>Mindestens ein Tool wird aufgerufen</>
        ) : (
          <>
            <ToolName name={p.tool} /> wird aufgerufen
          </>
        )
      break
    case 'tool-not-called':
      body =
        str(p.tool) === '*' ? (
          <>
            <strong>Kein</strong> Tool wird aufgerufen
          </>
        ) : (
          <>
            <ToolName name={p.tool} /> wird <strong>nicht</strong> aufgerufen
          </>
        )
      break
    case 'max-tool-calls':
      body = str(p.max) === '0' ? <>Kein einziger Tool-Aufruf</> : <>Höchstens {str(p.max) || '…'} Tool-Aufrufe</>
      break
    case 'json-valid':
      body = bool(p.strict, false) ? <>Antwort ist reines JSON, ohne Text drumherum</> : <>Antwort enthält gültiges JSON</>
      break
    case 'json-path':
      body = str(p.value) ? (
        <>
          JSON-Feld <Code text={p.path} /> ist <Quote text={p.value} />
        </>
      ) : (
        <>
          JSON-Feld <Code text={p.path} /> ist vorhanden
        </>
      )
      break
    case 'judge-questions': {
      const questions = listValue(p.questions)
      body = (
        <>
          <JudgeModel id={p.model} /> beantwortet {questions.length === 1 ? 'eine Frage' : `${questions.length} Fragen`} mit Ja
          {detail && questions.length > 0 && (
            <ul className="eval-questions">
              {questions.map((q, i) => (
                <li key={i}>{q}</li>
              ))}
            </ul>
          )}
        </>
      )
      break
    }
    case 'llm-judge':
      body = (
        <>
          <JudgeModel id={p.model} /> prüft <Quote text={p.criterion} />
        </>
      )
      break
    default:
      body = (
        <>
          <code className="eval-code">{config.type}</code>{' '}
          {Object.entries(p)
            .map(([k, v]) => `${k}=${Array.isArray(v) ? v.join(' | ') : str(v)}`)
            .join(' · ')}
        </>
      )
  }
  return <span className="eval-sentence">{body}</span>
}
