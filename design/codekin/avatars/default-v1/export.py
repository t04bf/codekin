from pathlib import Path
import subprocess,json
root=Path(__file__).resolve().parent
names=['wink','glasses','laugh','star','headphones','calm','blush','curious','cap','sunglasses','freckles','sparkle']
labels=['暖橙眨眼','森林绿眼镜','青绿欢笑','金黄星星','蓝色耳机','紫色安静','粉色开心','浅绿好奇','棕色帽子','杏色墨镜','深蓝雀斑','珊瑚闪光']
# Square crops follow the actual generated arrangement, not the canvas's blank margins.
xs=[8,318,632,941];ys=[92,446,798]
items=[]
for i,(name,label) in enumerate(zip(names,labels)):
 stem=f'avatar-{i+1:02d}-{name}'
 subprocess.run(['/opt/homebrew/bin/ffmpeg','-v','error','-y','-i',str(root/'overview.png'),'-vf',f'crop=305:305:{xs[i%4]}:{ys[i//4]},scale=512:512:flags=lanczos','-frames:v','1',str(root/f'{stem}.png')],check=True)
 items.append({'id':f'codekin-avatar-{i+1:02d}','name':label,'file':f'{stem}.png','width':512,'height':512})
(root/'manifest.json').write_text(json.dumps({'version':1,'avatars':items},ensure_ascii=False,indent=2))
