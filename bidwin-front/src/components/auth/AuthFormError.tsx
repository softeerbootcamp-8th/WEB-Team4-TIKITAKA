interface AuthFormErrorProps {
  /* 입력 필드의 aria-describedby가 가리키는 id */
  id: string
  message: string
}

/*
 * 인증 화면의 폼 단위 오류 표시. 필드별 문구를 여러 곳에 흩뿌리지 않고
 * 입력 영역과 제출 버튼 사이 한 자리에서만 보여준다.
 */
function AuthFormError({ id, message }: AuthFormErrorProps) {
  return (
    <p
      id={id}
      role="alert"
      className="rounded-sm bg-down-tint px-base py-sm text-sm font-medium text-down"
    >
      {message}
    </p>
  )
}

export default AuthFormError
export type { AuthFormErrorProps }
