// 커밋 컨벤션: `{type}: {설명} (#{이슈번호})` (예: `feat: 입찰 API 컨트롤러 생성 (#12)`)
module.exports = {
  parserPreset: {
    parserOpts: {
      headerPattern: /^(feat|fix|refactor|chore|docs): (.+) \(#(\d+)\)$/,
      headerCorrespondence: ['type', 'subject', 'issue'],
    },
  },
  rules: {
    'type-empty': [2, 'never'],
    'subject-empty': [2, 'never'],
    'header-max-length': [2, 'always', 100],
  },
};
