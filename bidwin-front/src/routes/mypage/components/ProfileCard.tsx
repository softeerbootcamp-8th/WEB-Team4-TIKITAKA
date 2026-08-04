import { Settings } from 'lucide-react'
import Button from '../../../components/ui/Button'
import Card from '../../../components/ui/Card'
import { formatDate } from '../../../lib/format'
import { PROFILE_TEXT } from '../constants'
import type { MyProfile } from '../types'
import ProfileAvatar from './ProfileAvatar'

const AVATAR_CLASS = 'h-20 w-20 text-2xl'
const MANAGE_ICON_SIZE = 16

function ProfileCard({ profile, onManage }: { profile: MyProfile; onManage: () => void }) {
  return (
    <Card className="flex flex-wrap items-center gap-base sm:flex-nowrap sm:gap-lg">
      <ProfileAvatar
        nickname={profile.nickname}
        imageUrl={profile.profileImageUrl}
        className={AVATAR_CLASS}
      />

      <div className="min-w-0 flex-1">
        <p className="truncate text-xl font-bold text-ink">{profile.nickname}</p>
        <p className="mt-xxs text-sm text-muted">
          {formatDate(new Date(profile.joinedAt))}
          {PROFILE_TEXT.joinedSuffix}
          <span className="px-xs text-hairline">|</span>
          {profile.sellCount}
          {PROFILE_TEXT.sellCountSuffix}, {profile.auctionJoinCount}
          {PROFILE_TEXT.joinCountSuffix}
        </p>
      </div>

      {/* 설정 목록에도 같은 항목이 있지만, 프로필 옆이 가장 먼저 찾게 되는 자리라 함께 둔다. */}
      <Button variant="secondary" onClick={onManage} className="w-full sm:w-auto">
        <Settings size={MANAGE_ICON_SIZE} />
        {PROFILE_TEXT.manage}
      </Button>
    </Card>
  )
}

export default ProfileCard
