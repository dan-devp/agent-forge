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
  return t ? <Ref kind="model" id={t} /> : <span className="eval-missing">Judge Model missing</span>
}

/**
 * One eval as a sentence: 'Answer contains "capital"' instead of contains text=capital.
 * A missing value shows as "…", so an incomplete eval is visible in the sentence itself.
 */
export function EvalSentence({ config, detail = true }: { config: EvalConfig; detail?: boolean }) {
  const p = config.params
  let body: ReactNode
  switch (config.type) {
    case 'contains':
      body = (
        <>
          Answer contains <Quote text={p.text} />
          {bool(p['ignore-case'], false) && <span className="muted"> (ignoring case)</span>}
        </>
      )
      break
    case 'not-contains':
      body = (
        <>
          Answer does <strong>not</strong> contain <Quote text={p.text} />
          {bool(p['ignore-case'], false) && <span className="muted"> (ignoring case)</span>}
        </>
      )
      break
    case 'equals':
      body = (
        <>
          Answer is exactly <Quote text={p.text} />
          {!bool(p['ignore-case'], true) && <span className="muted"> (case-sensitive)</span>}
        </>
      )
      break
    case 'regex':
      body = (
        <>
          Answer matches <Code text={p.pattern} />
        </>
      )
      break
    case 'not-regex':
      body = (
        <>
          Answer does <strong>not</strong> match <Code text={p.pattern} />
        </>
      )
      break
    case 'length': {
      const unit = str(p.unit) === 'chars' ? 'characters' : 'words'
      const min = str(p.min)
      const max = str(p.max)
      body =
        min && max ? (
          <>
            Answer has between {min} and {max} {unit}
          </>
        ) : max ? (
          <>
            Answer has at most {max} {unit}
          </>
        ) : min ? (
          <>
            Answer has at least {min} {unit}
          </>
        ) : (
          <>
            Answer length <span className="eval-missing">without a limit</span>
          </>
        )
      break
    }
    case 'tool-called':
      body =
        str(p.tool) === '*' ? (
          <>At least one Tool is called</>
        ) : (
          <>
            <ToolName name={p.tool} /> is called
          </>
        )
      break
    case 'tool-not-called':
      body =
        str(p.tool) === '*' ? (
          <>
            <strong>No</strong> Tool is called
          </>
        ) : (
          <>
            <ToolName name={p.tool} /> is <strong>not</strong> called
          </>
        )
      break
    case 'max-tool-calls':
      body = str(p.max) === '0' ? <>Not a single Tool call</> : <>At most {str(p.max) || '…'} Tool calls</>
      break
    case 'json-valid':
      body = bool(p.strict, false) ? <>Answer is pure JSON, with no text around it</> : <>Answer contains valid JSON</>
      break
    case 'json-path':
      body = str(p.value) ? (
        <>
          JSON field <Code text={p.path} /> is <Quote text={p.value} />
        </>
      ) : (
        <>
          JSON field <Code text={p.path} /> exists
        </>
      )
      break
    case 'judge-questions': {
      const questions = listValue(p.questions)
      body = (
        <>
          <JudgeModel id={p.model} /> answers {questions.length === 1 ? 'one question' : `${questions.length} questions`} with yes
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
          <JudgeModel id={p.model} /> checks <Quote text={p.criterion} />
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
