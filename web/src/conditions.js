// Mirrors ConnectionCondition.parse in flow: whitespace, AND, comparisons and period.
export function parseCondition(source) {
  const compact = String(source ?? '').replace(/\s+/g, '');
  if (!compact) return () => true;
  const predicates = compact.split('&').map(part => {
    const match = /^(<=|>=|<|>|%)(-?\d+)$/.exec(part);
    if (!match) throw new Error(`Invalid connection condition: ${part}`);
    const [, op, raw] = match, n = Number(raw);
    if (!Number.isSafeInteger(n) || (op === '%' && n <= 0)) throw new Error(`Invalid connection condition: ${part}`);
    return tick => op === '<' ? tick < n : op === '>' ? tick > n : op === '<=' ? tick <= n : op === '>=' ? tick >= n : tick % n === 0;
  });
  return tick => predicates.every(test => test(tick));
}

export function appendConditionClause(source, operator, tick) {
  if (!['%', '<', '>'].includes(operator) || !Number.isSafeInteger(tick) || tick < (operator === '%' ? 1 : 0)) throw new Error('Enter a valid tick number');
  const next = `${String(source ?? '').trim()}${String(source ?? '').trim() ? ' & ' : ''}${operator}${tick}`;
  parseCondition(next);
  return next;
}
