<p align="center">
  <h1 align="center">🏆 BidWin (비드윈)</h1>
  <p align="center">
    <strong>급하게 처분해야 할 물건을 위한 하향경매 플랫폼</strong>
  </p>
</p>

<br>

<p align="center">
  <a href="#1-프로젝트-소개">프로젝트 소개</a> •
  <a href="#2-시스템-구성도">시스템 구성도</a> •
  <a href="#3-기술-스택">기술 스택</a> •
  <a href="#4-서비스-상세-설명">서비스 상세 설명</a> •
  <a href="#5-문서화">문서화</a> •
  <a href="#6-그라운드-룰">그라운드 룰</a> •
  <a href="#7-팀-구성">팀 구성</a>
</p>

<hr>

## 1. 프로젝트 소개
**Bidwin**은 판매자와 구매자가 적정 거래 가격을 더 빠르게 찾을 수 있도록 돕는 경매 기반 중고 거래 서비스입니다.

판매자는 상향 경매, 하향 경매, 즉시 구매 중 상황에 맞는 방식을 선택할 수 있습니다. Bidwin은 시간에 따른 가격 변화와 구매자 간 경쟁을 활용해 기존 중고 거래의 긴 판매 대기시간과 반복적인 가격 조정 문제를 해결합니다.

### 핵심 기능

#### 🌟 하향 경매

- 판매자가 설정한 시작 가격에서 일정 시간이 지날 때마다 **가격이 단계적으로 내려가는** 경매 방식입니다.
- 구매자는 원하는 가격이 될 때까지 기다릴 수 있지만, 다른 사용자가 먼저 구매할 가능성도 있어 **가격과 구매 기회 사이의 긴장감**을 경험하게 됩니다. 판매자는 가격 조정을 통해 상품의 판매 가능성을 높일 수 있습니다.

#### 🌟 즉시 구매

- 첫 입찰이 발생하기 전까지 판매자가 설정한 **즉시 구매 가격으로 상품을 바로 구매**할 수 있는 기능입니다.
- 구매자는 입찰 경쟁과 대기 없이 상품을 확정적으로 구매할 수 있고, 판매자는 원하는 가격에 상품을 빠르게 판매할 기회를 얻습니다.

#### 🌟 상향 경매

- 사용자들이 현재 최고 입찰가보다 높은 금액을 제시하며 경쟁하는 **일반적인 입찰 방식의 경매**입니다.
- 입찰 경쟁을 통해 상품의 가격이 자연스럽게 형성되며, 구매자는 원하는 상품을 확보하는 재미를, 판매자는 더 높은 가격에 판매할 기회를 얻습니다.
---

## 2. 시스템 구성도

### 2.1 시스템 아키텍처

*(추가 예정)*

### 2.2 ERD

![BidWin ERD](docs/images/erd.png)

---

## 3. 기술 스택

| 구분 | 스택 |
| :---: | --- |
| **프론트엔드** | ![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=flat&logo=typescript&logoColor=white) ![React](https://img.shields.io/badge/React-61DAFB?style=flat&logo=react&logoColor=black) |
| **백엔드** | ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=flat&logo=springboot&logoColor=white) ![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=flat&logo=mysql&logoColor=white) ![Redis](https://img.shields.io/badge/Redis-DC382D?style=flat&logo=redis&logoColor=white) ![Flyway](https://img.shields.io/badge/Flyway-CC0200?style=flat&logo=flyway&logoColor=white) |
| **인프라** | ![Amazon S3](https://img.shields.io/badge/Amazon%20S3-569A31?style=flat&logo=amazons3&logoColor=white) ![Amazon EC2](https://img.shields.io/badge/Amazon%20EC2-FF9900?style=flat&logo=amazonec2&logoColor=white) ![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-2088FF?style=flat&logo=githubactions&logoColor=white) |
| **협업** | ![Notion](https://img.shields.io/badge/Notion-000000?style=flat&logo=notion&logoColor=white) ![Figma](https://img.shields.io/badge/Figma-F24E1E?style=flat&logo=figma&logoColor=white) ![Slack](https://img.shields.io/badge/Slack-4A154B?style=flat&logo=slack&logoColor=white) |

---

## 4. 서비스 상세 설명

<details>
<summary><b>📖 기획 배경 및 문제 정의 (클릭하여 펼치기)</b></summary>

<br>

### 4.1 기획 배경

> 이사 당일, 아직 사용할 수 있는 매트리스를 결국 버려야 했다.  
> 조금 더 빠르게 가격을 조정할 수 있었다면 판매할 수 있지 않았을까?

기존 중고 거래는 판매자가 가격을 정해 상품을 등록한 뒤, 적절한 구매자가 나타날 때까지 기다리는 방식이 일반적입니다.

하지만 **이사나 공간 정리처럼 처분 기한이 정해진 상황에서는 구매자를 기다릴 시간이 부족**합니다. 판매자가 가격을 낮춰서라도 빠르게 판매하고 싶어도, 적절한 가격을 알기 어려워 직접 가격을 반복해서 수정해야 합니다.

**BidWin**은 이러한 문제를 해결하기 위해 시간의 흐름과 구매자 간 경쟁을 활용하여 거래 가격을 탐색하는 경매 방식을 도입했습니다.

### 4.2 사용자 페인포인트

#### 판매자

- 상품이 언제 판매될지 예측하기 어렵습니다.
- 빠르게 판매하려면 가격을 얼마나 낮춰야 하는지 판단하기 어렵습니다.
- 구매자의 반응을 확인하며 가격을 반복해서 수정해야 합니다.
- 처분 기한 안에 판매하지 못하면 아직 사용할 수 있는 물품도 폐기해야 합니다.
- 빠른 판매와 높은 판매 가격 중 어떤 선택이 유리한지 판단하기 어렵습니다.

#### 구매자

- 판매자와 가격을 협상하는 과정에서 시간과 노력이 필요합니다.
- 상품의 적정 가격을 판단하기 어렵습니다.
- 가격이 내려가기를 기다리다가 다른 구매자에게 상품을 놓칠 수 있습니다.
- 구매 의사가 있어도 판매자와 희망 가격이 달라 거래가 지연될 수 있습니다.

### 4.3 문제 정의

핵심 문제는 판매자가 **가격을 낮출 의향이 있어도**, 구매자가 **거래를 결정할 가격을 빠르게 찾기 어렵다는 점**입니다.

고정된 가격으로 구매자를 기다리는 기존 방식에서는 판매자와 구매자의 희망 가격 차이가 자연스럽게 좁혀지지 않습니다. 그 결과 거래가 지연되거나, 판매 가능한 물품이 거래되지 못한 채 폐기될 수 있습니다.

### 서비스 목표

Bidwin은 다양한 경매 방식을 통해 판매자와 구매자가 적정 거래 가격을 더 빠르게 찾을 수 있도록 지원합니다.

- 판매자가 판매 목적과 **처분 기한에 맞는 거래 방식**을 선택할 수 있도록 합니다.
- 판매자의 반복적인 가격 수정과 구매자의 **협상 부담을 줄입니다.**
- 판매 가능한 중고 물품이 거래되지 못하고 **폐기되는 상황을 줄입니다.**

### 4.4 해결 방법

| 사용자 문제 | 해결 방법 |
| --- | --- |
| 가격을 반복해서 수정해야 함 | **하향 경매**를 통해 시간에 따라 가격을 자동으로 낮춤 |
| 상품의 적정 가격을 판단하기 어려움 | **상향 경매**를 통해 구매자 간 경쟁으로 가격을 형성 |
| 기다리지 않고 거래를 확정하고 싶음 | **즉시 구매**를 통해 판매자가 설정한 가격으로 바로 거래 |
| 판매 시점을 예측하기 어려움 | 경매 종료 시간을 설정해 거래 기한을 명확하게 제공 |

</details>

---

## 5. 문서화

| 문서명 | 상세 설명 | 링크 |
| :--- | :--- | :---: |
| **📋 기능 명세서** | 요구사항 명세서 및 이슈 정의 | [바로가기](https://docs.google.com/spreadsheets/d/1iq08mCo6T1AeusSlgDHwsqnZRK2bE6XQu9pcvFWykkY/edit?usp=sharing) |
| **🗄️ Database ERD** | 데이터베이스 테이블 구조 및 연관관계 정의서 | [바로가기](https://softeer04.notion.site/ERD-3ab6b34e4aac80a7b018e6b145691d41) |
| **📅 일정 관리 (GitHub Project)** | 스프린트 관리 및 Task 분배 | [바로가기](https://github.com/orgs/softeerbootcamp-8th/projects/8) |
| **📝 전체 문서 관리 (Notion)** | 매일 스크럼, 아이디어 회의, 주간 회고록 | [바로가기](https://softeer04.notion.site/HMG-Softeer-4-TIKITAKA-39f6b34e4aac80bcb442fdccba74ab57?source=copy_link) |

---

## 6. 그라운드 룰

<details>
<summary><b>🤝 [팀 Ground Rules] 우리 팀의 협업 & 소통 규칙 (클릭하여 펼치기)</b></summary>

<br>

### 1. 소통과 존중
* 팀원의 의견에는 먼저 긍정적으로 반응하고 끝까지 경청한다.
* 발언을 끊지 않는다. 중간에 의견을 더하고 싶다면 손을 들어 의사를 표시한다.
* 무조건 동의하지 않으며, 반대 의견은 감정이 아닌 논리와 기술적 근거로 설명한다.
* 불편한 감정이나 오해가 생기면 쌓아두지 말고 즉시 이야기한다.
* 감정적으로 행동하거나 말다툼이 예상되면 🦆**오리**🦆를 앞에 두고 차분하게 대화한다.
* 다른 팀원의 담당 범위를 수정하거나 대신 처리해야 할 때는 먼저 허락을 구한다.

### 2. 문제 및 진행 상황 공유
* 문제, 막힘, 지연 가능성이 생기면 해결 가능 여부와 관계없이 즉시 알린다.
* 막혔을 때는 짧게 스스로 고민한 뒤, 지체하지 않고 팀원에게 도움을 요청한다.
* 각자 진행 중인 작업과 현재 단계를 팀이 확인할 수 있도록 지속해서 공유한다.
* 요청사항과 업무 분담은 가능한 한 미리 정리하여 전달한다.
* 업무는 합의한 우선순위에 따라 처리하며, 모든 요청을 즉시 처리할 수 없음을 서로 이해한다.

### 3. 회의와 의사결정
* 매일 아침 10~20분간 데일리 스크럼을 진행한다.
  * 건강 상태
  * 전날 진행한 일
  * 오늘 할 일
  * 막힌 점과 도움이 필요한 부분
* 데일리 회의와 회고의 진행자는 매번 교대한다.
* 회의에는 종료 시간을 정하고, 논의가 길어지면 중재자의 진행을 따른다.
* 의견이 갈리면 기술적 근거를 바탕으로 짧은 토론을 진행한다.
* 결론이 나지 않으면 AI 또는 Dangle님의 조언을 참고하여 결정한다.
* 외부 마감 하루 전에는 팀 전체가 진행 상황과 결과물을 점검한다.

### 4. 기록과 업무 채널
* 결정 사항, 담당자, 일정, 변경 내용은 반드시 회의록에 기록한다.
* 회의·토론·개발 과정은 필요할 때 사진으로 남긴다.
* 공식 업무 소통은 지정된 슬랙 업무 채널에서 진행한다.
* 사적인 대화는 별도의 슬랙 채널을 이용한다.
* 구두로 합의한 중요한 내용도 공식 채널이나 회의록에 다시 남긴다.

### 5. 피드백
* 매일 짧게라도 건전한 피드백 시간을 갖는다.
* 피드백은 사람보다 행동과 결과물을 대상으로 한다.
* 솔직한 의견과 새로운 아이디어를 편하게 제시할 수 있는 분위기를 만든다.
* 피드백을 방어적으로 받아들이지 않고 개선을 위한 정보로 활용한다.

### 6. 건강과 휴식
* 매시 50분부터 정각까지 휴식한다.
* 장시간 계속 앉아 있지 않고 정기적으로 움직인다.
* 식사 시간을 지킨다. (점심: 오후 12시 30분 / 저녁: 오후 7시 00분)
* 밤샘을 지양하고 하루 최소 6시간 수면을 확보한다.
* 피로와 건강 상태가 업무와 태도에 영향을 줄 수 있음을 서로 배려한다.

### 7. 개발 원칙
* 개인의 작업이 다른 팀원의 작업에 영향을 줄 경우 변경 전에 알리고 협의한다.
* 일정뿐 아니라 유지보수성과 팀원이 이어서 작업할 수 있는 상태까지 고려한다.

</details>

---

## 7. 팀 구성

<table align="center" width="100%">
  <thead>
    <tr>
      <th align="center" width="20%">프로필 사진</th>
      <th align="center" width="25%">이름 / 역할</th>
      <th align="left" width="40%">담당 업무 및 기술 스택</th>
      <th align="center" width="15%">링크</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td align="center">*(이미지 영역)*</td>
      <td align="center"><b>김근성</b><br><sub>팀장, BE</sub></td>
      <td>
      </td>
      <td align="center">
        <a href="https://github.com"><img src="https://img.shields.io/badge/GitHub-181717?style=flat&logo=github&logoColor=white"/></a>
      </td>
    </tr>
    <tr>
      <td align="center">*(이미지 영역)*</td>
      <td align="center"><b>허찬욱</b><br><sub>BE</sub></td>
      <td>
      </td>
      <td align="center">
        <a href="https://github.com"><img src="https://img.shields.io/badge/GitHub-181717?style=flat&logo=github&logoColor=white"/></a>
      </td>
    </tr>
    <tr>
      <td align="center">*(이미지 영역)*</td>
      <td align="center"><b>노승억</b><br><sub>BE</sub></td>
      <td>
      </td>
      <td align="center">
        <a href="https://github.com"><img src="https://img.shields.io/badge/GitHub-181717?style=flat&logo=github&logoColor=white"/></a>
      </td>
    </tr>
  </tbody>
</table>

<br>
