export const DEPOSIT_CHANGED_EVENT = 'bidwin:deposit-changed'

export function notifyDepositChanged() {
  window.dispatchEvent(new Event(DEPOSIT_CHANGED_EVENT))
}
