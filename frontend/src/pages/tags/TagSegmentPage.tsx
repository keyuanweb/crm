import { Tabs } from 'antd'
import TagListPage from './TagListPage'
import SegmentListPage from './SegmentListPage'

/** 标签与细分容器页（031）。 */
export default function TagSegmentPage() {
  return (
    <Tabs
      defaultActiveKey="tags"
      items={[
        { key: 'tags', label: '标签管理', children: <TagListPage /> },
        { key: 'segments', label: '客户细分', children: <SegmentListPage /> },
      ]}
    />
  )
}
