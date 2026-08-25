import { useNavigate } from 'react-router-dom'
import { Button, Result } from 'antd'
import { HomeOutlined } from '@ant-design/icons'

/** 404 页面（布局优化）：未知路径友好提示，带返回首页入口。 */
export default function NotFoundPage() {
  const navigate = useNavigate()
  return (
    <div style={{ display: 'flex', minHeight: '60vh', alignItems: 'center', justifyContent: 'center' }}>
      <Result
        status="404"
        title="404"
        subTitle="抱歉，您访问的页面不存在"
        extra={
          <Button type="primary" icon={<HomeOutlined />} onClick={() => navigate('/', { replace: true })}>
            返回首页
          </Button>
        }
      />
    </div>
  )
}
