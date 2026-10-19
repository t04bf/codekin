import * as Avatar from '@radix-ui/react-avatar'
import mascot from '@/assets/mascot-avatar.png'

export function UserAvatar({ src, nickname, size = 80 }: { src?: string | null; nickname?: string | null; size?: number }) {
  return <Avatar.Root className="inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full border bg-white p-0.5" style={{ width: size, height: size }}>
    <Avatar.Image src={src || mascot} alt={`${nickname || '用户'}的头像`} className="h-full w-full rounded-full object-cover"/>
    <Avatar.Fallback className="h-full w-full"><img src={mascot} alt={`${nickname || '用户'}的头像`} className="h-full w-full rounded-full object-cover"/></Avatar.Fallback>
  </Avatar.Root>
}
